package wifi;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

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
		timeoutLimit = 500;//**want this to be 0.5 seconds
		minCWin = theRF.aCWmin;
		maxCWin = theRF.aCWmax;
		expBackOff = minCWin;
	}



	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes.
	 */
	public void run() {
		System.out.println("send is alive and well");

		boolean busy = false; //for checking ability to send
		boolean notACKed = true;


		while(true){

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


			//as long as there is something to send
			while(!abqSendAck.isEmpty()){

				/*
					as long as we haven't received an ACK, haven't
					timed out, and haven't hit our retry limit,
					keep re-sending the packet at intervals
				 */
				while(numRetry<retryLimit && notACKed && (expBackOff<maxCWin)){	
					
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

					while(((sendTime !=0 )&&(theRF.clock()-sendTime) < timeoutLimit) && notACKed){
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
									System.out.println("SendThread recognizes ack.");
									//stop the time
									rcvTime = theRF.clock();
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

					}
					
					System.out.println("out of first loop");

					if(notACKed){
						//keep sending

						if(theRF.inUse()){
							busy = true;
							System.out.println("Medium is NOT idle.");

							while(busy){
								System.out.println("Wait until current transmission ends.");
								while(theRF.inUse()){
									//wait until current transmission ends	
									System.out.println("Waiting for transmission to end.");
								}
								//not transmitting right now
								busy = false;
								//wait DIFS
								if(theRF.getIdleTime() > difs)
								{
									System.out.println("Waiting DIFS");
									//Check if open
									if(theRF.inUse()){
										//the RF layer is busy
										busy = true;
										System.out.println("Medium is NOT still idle.");
									}	
								}
							}
							//The channel is open now

							System.out.println("From SendThread(B) - Number of Retrys = " + numRetry);

							//wait exponential back-off time
							if(theRF.getIdleTime() > (randExpoBack.nextInt(expBackOff*numRetry))){
								System.out.println("Wait exponential backoff time while medium idle.");
								//send the data
								theRF.transmit(abqSendAck.element());
								numRetry = numRetry + 1; //count this retry
								//record when it was sent
								sendTime = theRF.clock();
								System.out.println("Transmit Frame0: Sent Packet");
							}
						}

						//Medium IS idle

						//wait IFS, Still idle?
						//wait DIFS
						if(theRF.getIdleTime() > difs)
						{
							System.out.println("Waiting DIFS");

							//Check if open
							if(theRF.inUse()){
								//the RF layer is busy
								busy = true;
								System.out.println("Medium is NOT idle anymore.");

								while(busy){
									System.out.println("Wait until current transmission ends.");
									while(theRF.inUse()){
										//wait until current transmission ends	
										System.out.println("Waiting for transmission to end.");
									}
									//not transmitting right now
									busy = false;
									//wait DIFS
									if(theRF.getIdleTime() > difs)
									{
										System.out.println("Waiting DIFS");
										//Check if open
										if(theRF.inUse()){
											//the RF layer is busy
											busy = true;
											System.out.println("Medium is NOT still idle.");
										}	
									}
								}
								//The channel is open now

								System.out.println("From SendThread(C) - Number of Retrys = " + numRetry);

								//wait exponential back-off time
								
								try {
									Thread.sleep(randExpoBack.nextInt(expBackOff));
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
									System.out.println("Waited exponential backoff time while medium idle.");
									//for next window size
									expBackOff = expBackOff *2;
									//send the data
									theRF.transmit(abqSendAck.element());
									numRetry = numRetry + 1; //count this retry
									//record when it was sent
									sendTime = theRF.clock();
									System.out.println("Transmit Frame1: Sent Packet");


								
							}

							System.out.println("The Medium is still idle.");
							//send the data
							theRF.transmit(abqSendAck.element());
							//record when it was sent
							sendTime = theRF.clock();
							System.out.println("Transmit Frame2: Sent Packet");
							numRetry = numRetry + 1;
						}

						
					}
					System.out.println("Number of Retrys in Send: " + numRetry);
				}

				//check if we have hit the retry limit
				if(numRetry >= retryLimit){
					System.out.println("Hit retry limit.");
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
				}

				//check to see if we received and ACK
				if(BuildPacket.rcvACK.get()){
					System.out.println("Packet was ACKed!!!!");
				}
				
				expBackOff = minCWin;
				System.out.println("reset exponential backoff.");
			}

		}


	}



}




