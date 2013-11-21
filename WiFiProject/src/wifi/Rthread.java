package wifi;

import java.lang.reflect.Array;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

/**
 * 
 * @author lswanson
 *
 */
public class Rthread implements Runnable {
	private  byte[] recPac;
	private  RF theRF;
	private ArrayBlockingQueue <byte []> ACKablockQ = new ArrayBlockingQueue(10);
	private short recDestAdd;
	private short recSrcAdd;
	private short recFrameType;
	private short recRetry;
	private short recSeqNum;
	private byte[] recData;
	private short recCRC;
	private boolean isData = false;
	private ArrayBlockingQueue <byte []> recABQ = new ArrayBlockingQueue(10);
	private short ourMAC;
	boolean dataInQ = false;


	/**
	 * A second constructor for the receiving thread
	 * @param packet
	 * @param dest
	 * @param theRF
	 * @param abq
	 */
	public Rthread(byte[]packet, short ourAdd, RF theRF, ArrayBlockingQueue abq)
	{
		//put recieved data here
		recABQ = abq;
		ourMAC = ourAdd;
		this.theRF = theRF;
		recPac = packet;
	}


	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes. 
	 */
	public void run() {
		System.out.println("Receive is alive and well");
		while(true)
		{
			if(theRF.dataWaiting()){
				//make a call to receive
				recPac = theRF.receive();

				//check to see if something was received
				if (recPac.length != 0){
					//then something was received and we need to figure out what it is
					//BuildPacket bp = new BuildPacket(recPac);
					//"shred" it to get all the pieces of the packet
					//bp.shred(recPac);
					//get some of the pieces from the packet
					recFrameType = BuildPacket.retFrameType(recPac);
					recDestAdd = BuildPacket.retDestAd(recPac);
					recSrcAdd = BuildPacket.retSrcAd(recPac);
					recSeqNum = BuildPacket.retSeqNum(recPac);
					recData = BuildPacket.retRecData(recPac);
					
					System.out.println("Gathered incoming packet info from:" + recSrcAdd);

					//check to see if the packet was for us
					//turn compare each byte
					if(recDestAdd == ourMAC){
						//the packet is for us!
						System.out.println("The packet is for us!");
						//check what type of packet we are receiving
						if(recFrameType == 0){
							//data packet!
							isData = true;
							System.out.println("Received a data packet.");
						}
						if(recFrameType == 1){
							//ACK packet!
							//it is an ACK
							System.out.println("Received an ACK packet.");
							//NEED TO CLEAR OUT ARRAY BLOCKING QUEUE using SeqNum
						}
						if(recFrameType == 2){
							//it is a Beacon packet
							System.out.println("Received a Beacon packet.");
						}
						if(recFrameType == 4){
							System.out.println("Received a CTS packet.");
						}
						if(recFrameType == 5){
							//it is an RTS
							System.out.println("Received a RTS packet.");
						}

						//if data packet, need to put into an A.B.Q. to be accessed in LinkLayer
						if(isData = true){
							//add the data to the ABQ
							recABQ.add(recData);
							dataInQ = true;
						}

						//reset booleans
						isData=false;	
					}

				}
			}

		}
	}

	/**
	 * A getter for the Link Layer return the first
	 * byte array in the received Array Blocking Queue 
	 * and removes it from the front.
	 * @return
	 */
	public byte[] getRecABQ(){
		byte[] toPass = null;
		try {
			toPass = recABQ.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
		return toPass;
	}

	/**
	 * 
	 * @return
	 */
	public boolean dataWaitinginQ(){
		
		if(dataInQ){
			dataInQ = false;//will be empty once removed
			return true;
		}
		
		return false;
	}

	/**
	 * 
	 * @return
	 */
	public byte[] getData(){
		//
		try {
			return recABQ.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * 
	 * @return
	 */
	public short getDestAdd(){
		return recDestAdd;
	}
	
	/**
	 * 
	 * @return
	 */
	public short getSrcAdd(){
		return recSrcAdd;
	}
	
}
