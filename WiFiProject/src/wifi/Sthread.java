package wifi;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import rf.RF;

/**
 *
 * @author lswanson
 *
 */
public class Sthread implements Runnable {

	private  byte[] packet;
	private  RF theRF;
	//array for packets to be sent
	private ArrayBlockingQueue <byte []> ablockQSent;
	//array that will hold sent packets waiting for an ACK
	private ArrayBlockingQueue <byte[]> abqSendAck = new ArrayBlockingQueue(10);
	//public static boolean timedOut = false;
	private double slotTime;
	private int expBackOff;
	private double difs;
	private double sifs;
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
	private byte [] blankBeacon = new byte[8];
	//for sending a beacon only once
	private boolean sendingBeacon = false;
	private Checksum crcVal = new CRC32();
	//need an array for addresses and sequence numbers
	//the order of packets interchanging with each address
	//increments on sending new packets

	//need an array to hold sent packets until they've been acked
	//order my seq num and address


	/**
	 * Another constructor for the sending thread.
	 *
	 * @param packet data to be sent
	 * @param dest destination address
	 * @param theRF the RF layer we're using
	 * @param abq the array blocking queue to hold the sent packet
	 */
	public Sthread( RF theRF, ArrayBlockingQueue abq)
	{
		//to hold sent packets waiting to be acked
		ablockQSent = abq;
		this.theRF=theRF;
		difs = 0.500;//is this right?
		sifs = theRF.aSIFSTime;
		slotTime = theRF.aSlotTime;
		retryLimit = theRF.dot11RetryLimit;
		maxPacketLength = theRF.aMPDUMaximumLength;
		sendTime = 0;
		//**experimental roundtrip time (ADD IEEE saftey margin!!)**
		timeoutLimit = 7978;
		minCWin = theRF.aCWmin;
		maxCWin = theRF.aCWmax;
		beaconFreq = LinkLayer.setBeacFreq;
		
		if(LinkLayer.fixedWin.get()==true){
			//use max everytime
			expBackOff = maxCWin;
			//will select the maximum value each time
  		  	//(maximum that the collision window allows)
		}else{
			//will select a value w/in the current collision window
  		  	//randomly when waiting after DIFS has elapsed
			expBackOff = randExpoBack.nextInt(maxCWin);
		}
		
		
	}



	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes.
	 */
	public void run() {
		LinkLayer.diagOut("send is alive and well");

		boolean busy = false; //for checking ability to send
		boolean notACKed = true;

		/*//*****TIMING TEST****
		//loop to make sure both clocks are running
		long timer = theRF.clock();
		while((theRF.clock() - timer)<5000){
			//wait five seconds
			System.out.println("The current clock is: " + theRF.clock());
		}
*/
		while(true){
			
			beaconFreq = LinkLayer.setBeacFreq;

			if(!ablockQSent.isEmpty()){
				//something wanting to be sent
				try {
					//add it to the array that will keep trying to resend
					//until it has been acked
					abqSendAck.add(ablockQSent.take());
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			//sending beacons
			if(((theRF.clock()+Rthread.fudge.get()-lastBeacon) > beaconFreq) || BuildPacket.shtSendDestAdd == -1){
				//send another beacon
				sendingBeacon = true;
				//give to BuildPacket
				byte[] beacon = BuildPacket.build(blankBeacon,(short) -1, LinkLayer.ourMAC, (short)16384);
				byte[] temp = new byte[8];
				//adding the current local time ***added time to create and transmit****
				System.arraycopy(ByteBuffer.wrap(temp).putLong(theRF.clock()+(long)3989 + Rthread.fudge.get()).array(), 0, beacon, 6, 8);
				System.out.println("Sending a BEACON!");
				System.out.println("The current local time is: " + theRF.clock()+Rthread.fudge.get());
				theRF.transmit(beacon);
				//start timer 
				lastBeacon = theRF.clock()+Rthread.fudge.get();
				//reset destination add for further use
				byte [] blah = new byte[1];
				BuildPacket.build(blah, (short)0, (short)0, (short)0);
				numRetry = 5;
			}


			//as long as there is something to send
			while(!abqSendAck.isEmpty() && !sendingBeacon){

				/*
				 * NOTNOT
					as long as we haven't received an ACK, haven't
					timed out, and haven't hit our retry limit,
					keep re-sending the packet at intervals
				 */
				while(numRetry<retryLimit && notACKed && (expBackOff<maxCWin)){	
					
				//if it is a beacon we only need to send it once
					if(sendingBeacon == true){
						//set the numRetry so we won't keep sending
						numRetry = retryLimit;
						//reset the boolean
						sendingBeacon = false;
						
					}
					
					//do we need to flip the retry bit
					if(numRetry==1){
						byte[] wholePacket = new byte[2048];
						try {
							wholePacket = abqSendAck.take();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						byte firstByte = wholePacket[0];
						//AND the first byte to flip the retry bit
						firstByte = (byte) (firstByte | (1 << 4)); 

						//put this new byte back into the packet
						wholePacket [0] = firstByte;
						//put this packet back into the ArrayBlockingQueue to be sent
						abqSendAck.add(wholePacket);
					}

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
									LinkLayer.diagOut("SendThread recognizes ack.");
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
						}
					}
					

					if(notACKed){
						//keep sending

						if(theRF.inUse()){
							busy = true;
							LinkLayer.diagOut("Medium is NOT idle.");

							while(busy){
								LinkLayer.diagOut("Wait until current transmission ends.");
								while(theRF.inUse()){
									//wait until current transmission ends	
									LinkLayer.diagOut("Waiting for transmission to end.");
								}
								//not transmitting right now
								busy = false;
								//wait DIFS
								if(theRF.getIdleTime() > difs+((theRF.clock()+Rthread.fudge.get())%50))
								{
									LinkLayer.diagOut("Waiting DIFS");
									//Check if open
									if(theRF.inUse()){
										//the RF layer is busy
										busy = true;
										LinkLayer.diagOut("Medium is NOT still idle.");
									}	
								}
							}
							//The channel is open now

							LinkLayer.diagOut("From SendThread(B) - Number of Retrys = " + numRetry);

							//wait exponential back-off time
							if(theRF.getIdleTime() > (randExpoBack.nextInt((expBackOff*numRetry)+1))){
								LinkLayer.diagOut("Wait exponential backoff time while medium idle.");
								//send the data
								theRF.transmit(abqSendAck.element());
								numRetry = numRetry + 1; //count this retry
								//record when it was sent
								sendTime = theRF.clock()+Rthread.fudge.get();
								LinkLayer.diagOut("Transmit Frame0: Sent Packet");
							}
						}

						//Medium IS idle

						//wait IFS, Still idle?
						//wait DIFS
						if(theRF.getIdleTime() > difs+((theRF.clock()+Rthread.fudge.get())%50))
						{
							LinkLayer.diagOut("Waiting DIFS");

							//Check if open
							if(theRF.inUse()){
								//the RF layer is busy
								busy = true;
								LinkLayer.diagOut("Medium is NOT idle anymore.");

								while(busy){
									LinkLayer.diagOut("Wait until current transmission ends.");
									while(theRF.inUse()){
										//wait until current transmission ends	
										LinkLayer.diagOut("Waiting for transmission to end.");
									}
									//not transmitting right now
									busy = false;
									//wait DIFS
									if(theRF.getIdleTime() > difs+((theRF.clock()+Rthread.fudge.get())%50))
									{
										LinkLayer.diagOut("Waiting DIFS");
										//Check if open
										if(theRF.inUse()){
											//the RF layer is busy
											busy = true;
											LinkLayer.diagOut("Medium is NOT still idle.");
										}	
									}
								}
								//The channel is open now

								LinkLayer.diagOut("From SendThread(C) - Number of Retrys = " + numRetry);

								//wait exponential back-off time
								
								try {
									Thread.sleep(randExpoBack.nextInt(expBackOff));
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
									LinkLayer.diagOut("Waited exponential backoff time while medium idle.");
									//for next window size
									expBackOff = expBackOff *2;
									//send the data
									theRF.transmit(abqSendAck.element());
									numRetry = numRetry + 1; //count this retry
									//record when it was sent
									sendTime = theRF.clock()+Rthread.fudge.get();
									LinkLayer.diagOut("Transmit Frame1: Sent Packet");


								
							}

							LinkLayer.diagOut("The Medium is still idle.");
							//send the data
							theRF.transmit(abqSendAck.element());
							//record when it was sent
							sendTime = theRF.clock()+Rthread.fudge.get();
							LinkLayer.diagOut("Transmit Frame2: Sent Packet");
							numRetry = numRetry + 1;
						}

						
					}
					LinkLayer.diagOut("Number of Retrys in Send: " + numRetry);
				}

				//check if we have hit the retry limit
				if(numRetry >= retryLimit){
					LinkLayer.diagOut("Hit retry limit.");
					//we need to stop trying to send this packet
					//and remove it from the ArrayBlockingQueue

					try {
						abqSendAck.take();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					//then we should reset the numRetry
					numRetry = 0;
					//we should also stop our timer
					sendTime=0;
					//update Status
					LinkLayer.setStatus.set(5);//TX_FAILED
				}

				//check to see if we received and ACK
				if(BuildPacket.rcvACK.get()){
					LinkLayer.diagOut("We recieved an ACK! Is it ours?");
				}
				
				expBackOff = minCWin;
				LinkLayer.diagOut("Resetting exponential backoff to original.");
			}
			//reset beacon boolean
			sendingBeacon = false;

		}


	}
	

}




