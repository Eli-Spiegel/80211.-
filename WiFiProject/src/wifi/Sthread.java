package wifi;

//import java.lang.reflect.Array;
//import java.math.BigInteger;
import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.IntBuffer;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import rf.RF;

/**
 * This is a Thread that run and continuously checks to 
 * see if there are packets waiting to be sent. This 
 * also handles the calling of methods to build packets
 * and also waits for ACKs on sent packets. The thread uses
 * proper wait and back offs as outlined in the 802.11~ protocol.
 * @author Lauren Swanson & Eli Spiegel
 * @version 12/15/13
 *
 */
public class Sthread implements Runnable {

	//private  byte[] packet; ********************
	private  RF theRF;
	//array for packets to be sent
	private ArrayBlockingQueue <byte []> ablockQSent;
	//array that will hold sent packets waiting for an ACK
	private ArrayBlockingQueue <byte[]> abqSendAck = new ArrayBlockingQueue(10);
	//public static boolean timedOut = false;
	private double slotTime;
	private int expBackOff;
	private long difs;
	private long sifs;
	private int retryLimit;
	private int numRetry = 0;
	private int maxPacketLength;//in bytes
	private long sendTime = 0;
	private long rcvTime;
	private long timeoutLimit;
	private Random randExpoBack = new Random();
	private int minCWin;
	private int maxCWin;
	//how often beacons are transmitted from us
	private long beaconFreq;
	//time since last beacon
	private long lastBeacon;
	//blank byte array for Beacons (long value 0, 128 bits)
	//private byte [] blankBeacon = new byte[8];*********************
	//for sending a beacon only once
	private boolean sendingBeacon = false;
	private Checksum crcVal = new CRC32(); //Where do we use this?*************
	boolean firstTime = true; //just starting the thread

	/**
	 * Constructor which gets the ArrayBlocking Queue of things to send
	 * and the RF layer that we are using
	 *
	 * @param theRF The RF layer we're using
	 * @param abq The array blocking queue to hold the sent packet
	 */
	public Sthread( RF theRF, ArrayBlockingQueue abq)
	{
		//to hold sent packets waiting to be acked
		ablockQSent = abq;
		this.theRF=theRF;
		difs =5000;//is this right?
		sifs = theRF.aSIFSTime;
		slotTime = theRF.aSlotTime; //WE NEED TO FIGURE OUT WHERE TO USE THIS or if we use something else****
		retryLimit = theRF.dot11RetryLimit;
		maxPacketLength = theRF.aMPDUMaximumLength;
		sendTime = 0;
		//experimental roundtrip time plus IEEE saftey margin, difs
		timeoutLimit = (long) (18020 + difs);
		minCWin = theRF.aCWmin;
		maxCWin = theRF.aCWmax;
		beaconFreq = LinkLayer.setBeacFreq;
	}

	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes.
	 */
	public void run() {
		LinkLayer.diagOut("Send Thread - send is alive and well");

		boolean busy = false; //used for checking ability to send
		boolean notACKed = true;
		
		if(firstTime){
			//send a beacon on the first time
			if(((theRF.clock()+Rthread.fudge.get()-lastBeacon) > beaconFreq) || BuildPacket.shtSendDestAdd == -1){
				//conditions make sure we haven't just sent a beacon and the 
				//that it is a beacon (going to everyone)
				sendingBeacon = true;
				byte[] temp = new byte[8]; //to hold our timestamp
				byte[] beacon = BuildPacket.build(ByteBuffer.wrap(temp).putLong(theRF.clock()+(long)9010 + Rthread.fudge.get()).array(),(short) -1, LinkLayer.ourMAC, (short)16384);
				LinkLayer.diagOut("Send Thread - Sending our start-up BEACON!");
				theRF.transmit(beacon);//send the beacon
				//start timer to know when to send the next beacon
				lastBeacon = theRF.clock()+Rthread.fudge.get();
				//reset boolean so we don't keep sending
				firstTime=false;	
			}
		}
		
		while(true){

			beaconFreq = LinkLayer.setBeacFreq; //begins at 30sec

			if(!ablockQSent.isEmpty()){
				//something wanting to be sent
				try {
					//add it to the array that will keep trying to resend
					//until it has been acked
					abqSendAck.add(ablockQSent.take());
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					//update status
					LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
				}
			}
			
			//as long as there is something to send and we aren't currently sending a beacon
			while(!abqSendAck.isEmpty() && !sendingBeacon){
				
				if(((theRF.clock()+Rthread.fudge.get()-lastBeacon) > beaconFreq) || BuildPacket.shtSendDestAdd == -1){
					//time to send another beacon or BCast
					sendingBeacon = true;
					//get the adjusted time to put in our timestamp
					byte[] ourtime = BuildPacket.bitshifttime(theRF.clock()+(long)9010 + Rthread.fudge.get());
					//build our beacon
					byte[] beacon = BuildPacket.build(ourtime,(short) -1, LinkLayer.ourMAC, (short)16384);
				
					LinkLayer.diagOut("Send Thread - Sending a BEACON!");//diagnostics
					theRF.transmit(beacon);//sent it 
					lastBeacon = theRF.clock()+Rthread.fudge.get();//start timer
					//set the retrys so we will hit the limit and not wait for ACKs on the beacons
					numRetry = 5;
				}
				/*
				 * as long as we haven't hit the retry limit, been ACKed,
				 * or haven't increased the expBackOff too much...
				 */
				while(numRetry<retryLimit && notACKed && (expBackOff<maxCWin)){	

					//if it is a beacon make sure we only send it once. 
					if(sendingBeacon == true){
						//set the numRetry so we won't keep sending
						numRetry = retryLimit;
						//reset the boolean
						sendingBeacon = false;
					}

					//do we need to flip the retry bit?
					if(numRetry==1){//yep!
						byte[] wholePacket = new byte[2048];
						try {
							wholePacket = abqSendAck.take();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							//update status
							LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
						}
						byte firstByte = wholePacket[0];
						//AND the first byte to flip the retry bit
						firstByte = (byte) (firstByte | (1 << 4)); 

						//put this new byte back into the packet
						wholePacket [0] = firstByte;
						//put this packet back into the ArrayBlockingQueue to be sent
						abqSendAck.add(wholePacket);
					}

					//as long as we sent something and we haven't timed out and we haven't been acked
					while(((sendTime !=0 )&&(theRF.clock()+Rthread.fudge.get()-sendTime) < timeoutLimit) && notACKed){
						//check if we received an ACK
						if(BuildPacket.rcvACK.get()){
							//check that the ACK was for the packet we just sent
							short ackAdd = BuildPacket.shtRecSrcAdd;

							if(ackAdd == BuildPacket.shtSendDestAdd){
								//then it is for the right packet
								//check that the sequence number matches
								short ackSeqNum = BuildPacket.shtRecSeqNum;
								short sentSeqNum = BuildPacket.shtSendSeqNum;
								if(ackSeqNum == sentSeqNum){
									//the packet is for us and 
									//is responding to the packet we just sent
									notACKed = false;
									LinkLayer.diagOut("Send Thread - We recognize the ACK!");
									//update status
									LinkLayer.setStatus.set(4); //TX_DELIVERED
									//stop the time
									rcvTime = theRF.clock()+Rthread.fudge.get();
									//remove the acked packet from the ABQ holding it
									try {
										abqSendAck.take();
										numRetry = 0;//reset retry
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
										//update status
										LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
									}
								}
								//not the right seqNum
							}
							//not an ack for the right packet?
						}

						//sleep before checking for an ACK
						try {
							Thread.sleep(6000);//roundtrip time is ~8000
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							//update status
							LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
						}
					}

					if(notACKed){
						//keep sending

						if(theRF.inUse()){
							busy = true;
							LinkLayer.diagOut("Send Thread - Medium is NOT idle.");

							while(busy){
								LinkLayer.diagOut("Send Thread - Wait until current transmission ends.");
								while(theRF.inUse()){
									//wait until current transmission ends	
									LinkLayer.diagOut("Send Thread - Waiting for transmission to end.");
								}
								//not transmitting right now
								busy = false;
								//wait DIFS
								if(theRF.getIdleTime() > difs+(50 - ((theRF.clock()+Rthread.fudge.get())%50)))
								{
									LinkLayer.diagOut("Send Thread - Waiting DIFS");
									//Check if open
									if(theRF.inUse()){
										//the RF layer is busy
										busy = true;
										LinkLayer.diagOut("Sent Thread - Medium is NOT still idle.");
									}	
								}
							}
							//The channel is open now

							LinkLayer.diagOut("Send Thread(B) - Number of Retrys = " + numRetry);

							//wait exponential back-off time
							if(theRF.getIdleTime() > (randExpoBack.nextInt((expBackOff*numRetry)+1))){
								LinkLayer.diagOut("Send Thread -Wait exponential backoff time while medium idle.");
								//send the data
								theRF.transmit(abqSendAck.element());
								numRetry = numRetry + 1; //count this retry
								//record when it was sent
								sendTime = theRF.clock()+Rthread.fudge.get();
								LinkLayer.diagOut("Send Thread - Transmit Frame0: Sent Packet");
							}
						}

						//Medium IS idle
						//wait DIFS plus some
						if(theRF.getIdleTime() > difs+((theRF.clock()+Rthread.fudge.get())%50))
						{
							LinkLayer.diagOut("Send Thread - Waiting DIFS");

							//Check if open
							if(theRF.inUse()){
								//the RF layer is busy
								busy = true;
								LinkLayer.diagOut("Send Thread - Medium is NOT idle anymore.");

								while(busy){
									LinkLayer.diagOut("Send Thread - Wait until current transmission ends.");
									while(theRF.inUse()){
										//wait until current transmission ends	
										LinkLayer.diagOut("Send Thread - Waiting for transmission to end.");
									}
									//not transmitting right now
									busy = false;
									//wait DIFS
									if(theRF.getIdleTime() > difs+(50-((theRF.clock()+Rthread.fudge.get())%50)))
									{
										LinkLayer.diagOut("Send Thread - Waiting DIFS");
										//Check if open
										if(theRF.inUse()){
											//the RF layer is busy
											busy = true;
											LinkLayer.diagOut("Send Thread - Medium is NOT still idle.");
										}	
									}
								}
								//The channel is open now

								LinkLayer.diagOut("Send Thread(C) - Number of Retrys = " + numRetry);

								//wait exponential back-off time

								//check which wait type we should use (random or fixed)
								if(LinkLayer.fixedWin.get() == true){
									try {
										//COMMAND 2 SET TO FIXED
										Thread.sleep(randExpoBack.nextInt(expBackOff));
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
										//update status
										LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
									}
								}else{
									//pick the greatest (expbackoff)
									try {
										Thread.sleep(expBackOff);
									} catch (InterruptedException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
										//update status
										LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
									}
								}

								LinkLayer.diagOut("Send Thread - Waited exponential backoff time while medium idle.");
								//for next window size
								expBackOff = expBackOff *2;
								//send the data
								theRF.transmit(abqSendAck.element());
								numRetry = numRetry + 1; //count this retry
								//record when it was sent
								sendTime = theRF.clock()+Rthread.fudge.get();
								LinkLayer.diagOut("Send Thread - Transmit Frame1: Sent Packet");



							}

							LinkLayer.diagOut("Send Thread - The Medium is still idle.");
							//send the data
							theRF.transmit(abqSendAck.element());
							//record when it was sent
							sendTime = theRF.clock()+Rthread.fudge.get();
							LinkLayer.diagOut("Send Thread - Transmit Frame2: Sent Packet");
							numRetry = numRetry + 1;
						}


					}
					LinkLayer.diagOut("Send Thread - Number of Retrys: " + numRetry);
				}

				//check if we have hit the retry limit
				if(numRetry >= retryLimit){
					LinkLayer.diagOut("Send Thread - Hit retry limit.");
					//we need to stop trying to send this packet
					//and remove it from the ArrayBlockingQueue
					numRetry = 0;
					try {
						abqSendAck.take();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						//update status
						LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
					}
					//then we should reset the numRetry
					
					//we should also stop our timer
					sendTime=0;
					//update Status
					LinkLayer.setStatus.set(5);//TX_FAILED
				}

				expBackOff = minCWin;

			}
			//reset beacon boolean
			sendingBeacon = false;

		}


	}


}




