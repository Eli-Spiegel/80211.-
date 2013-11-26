package wifi;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
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
	//array for packets being sent (and not acked?)
	private ArrayBlockingQueue <byte []> ablockQSent;
	public static boolean timedOut = false;
	private double slotTime;
	private double expBackOff;
	private double difs;
	private double sifs;
	private int retryLimit;
	private int numRetry;
	private int maxPacketLength;//in bytes
	private long sendTime;
	private long timeoutLimit;
	
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
		expBackOff = slotTime;//**UPDATE THIS**
		retryLimit = theRF.dot11RetryLimit;
		numRetry = 0;
		maxPacketLength = theRF.aMPDUMaximumLength;
		sendTime = 0;
		timeoutLimit = 500;//**want this to be 0.5 seconds
	}
    
    
    
	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes.
	 */
	public void run() {
		System.out.println("send is alive and well");

		boolean busy = false; //for checking ability to send
		boolean atRetryLimit = false; //if we have retried too many times


		while(true){//do we still need this?	
			//System.out.println("Just in the this vague while loop.");
			
			//as long as there is something to send
			while(!ablockQSent.isEmpty()){
				
				/*long timeWaiting = theRF.clock()-sendTime;//how long have we been waiting?
				if(timeWaiting >= timeoutLimit){
					//we've been waiting too long!
					System.out.println("Reached Timeout Limit!");
					timedOut = true;
				}*/

				/*
					as long as we haven't received and ACK, haven't
					timed out
						add (&& !timedOut) to while conditions
					, and haven't hit our retry limit,
					keep re-sending the packet at intervals
				 */
				while(!BuildPacket.rcvACK && !atRetryLimit){
					
					System.out.println("haven't received ACK, timed out, or hit retry limit.");
					
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
						numRetry = numRetry + 1; //count this retry
						//have we retried too many times?
						if(numRetry >= retryLimit){
							System.out.println("Last retry attempt.");
							//yes we have
							atRetryLimit = true;
						}
						//wait exponential back-off time
						if(theRF.getIdleTime() > (expBackOff*numRetry)){
							System.out.println("Wait exponential backoff time while medium idle.");
							//transmit
							try {
								//send the data
								theRF.transmit(ablockQSent.take());
								//record when it was sent
								sendTime = theRF.clock();
								System.out.println("Transmit Frame0: Sent Packet");
							}

							catch (InterruptedException e) {
								// TODO Auto-generated catch block
								System.out.println(e);
								//e.printStackTrace();

							}
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
							numRetry = numRetry + 1; //count this retry
							//have we retried too many times?
							if(numRetry >= retryLimit){
								System.out.println("Last retry attempt.");
								//yes we have
								atRetryLimit = true;
							}
							//wait exponential back-off time
							if(theRF.getIdleTime() > (expBackOff*numRetry)){
								System.out.println("Wait exponential backoff time while medium idle.");
								//transmit
								try {
									//send the data
									theRF.transmit(ablockQSent.take());
									//record when it was sent
									sendTime = theRF.clock();
									System.out.println("Transmit Frame1: Sent Packet");
								}

								catch (InterruptedException e) {
									// TODO Auto-generated catch block
									System.out.println(e);
									//e.printStackTrace();

								}
							}
						}
						System.out.println("The Medium is still idle.");
						try {
							//send the data
							theRF.transmit(ablockQSent.take());
							//record when it was sent
							sendTime = theRF.clock();
							System.out.println("Transmit Frame2: Sent Packet");
						}

						catch (InterruptedException e) {
							// TODO Auto-generated catch block
							System.out.println(e);
							//e.printStackTrace();

						}
					}
				}
				//should have received and ACK
				if(BuildPacket.rcvACK){
					System.out.println("Packet was ACKed!!!!");
				}

				//should have either sent the packet or timed out
				/*if(timedOut){
					System.out.println("TIMEDOUT!!!!");
				}*/
				
				//reset the retry count
				numRetry = 0;	
				
			}
			
		}

	}
}

