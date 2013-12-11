package wifi;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

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
	//static to be used in send thread
	public static long rcvTime;
	private ArrayBlockingQueue <byte []> recABQ = new ArrayBlockingQueue(10);
	private short ourMAC;
	boolean dataInQ = false;
	public short ackshort = 8192;
	private HashMap<Short,Short> theRTable= new HashMap<Short,Short>();
	Checksum crcVal = new CRC32();

	//need an array for addresses and sequence numbers
	//the order of packets interchanging with each address
	//increments on receive new acceptable packet
	//add to array if receive from new address

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
		rcvTime = 0;
	}


	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes. 
	 */
	public void run() {
		LinkLayer.diagOut("Receive is alive and well");
		while(true)
		{
			if(theRF.dataWaiting()){
				//make a call to receive
				recPac = theRF.receive();

				//******TIMING TEST****
				System.out.println("The recieveing time is: " +theRF.clock());

				//check to see if something was received
				if (recPac.length != 0){
					//then something was received and we need to figure out what it is
					rcvTime = theRF.clock();
					//BuildPacket bp = new BuildPacket(recPac);
					//"shred" it to get all the pieces of the packet
					//bp.shred(recPac);
					//get some of the pieces from the packet

					//check if it is for us
					recDestAdd = BuildPacket.retDestAd(recPac);
					recSrcAdd = BuildPacket.retSrcAd(recPac);
					LinkLayer.diagOut("Gathered incoming packet info from:" + recSrcAdd);

					if(recDestAdd == ourMAC || recDestAdd == -1){

						//each time this is called it starts with all false booleans for frame types 
						recFrameType = BuildPacket.retFrameType(recPac);
						recRetry = BuildPacket.retRetry(recPac);
						recSeqNum = BuildPacket.retSeqNum(recPac);
						recData = BuildPacket.retRecData(recPac);

						LinkLayer.diagOut("the recDestAdd is: "+recDestAdd+" and the ourMAC is: " +ourMAC);

						//check to see if the packet was for us
						//turn compare each byte


						//the packet is for us!
						LinkLayer.diagOut("The packet is for us!");
						//check what type of packet we are receiving
						//BuildPacket.rcvData.getAndSet(false);
						if(BuildPacket.rcvData.get()){
							//data packet!
							isData = true;
							LinkLayer.diagOut("Received a data packet.");
							if(!theRTable.containsKey(recSrcAdd)||theRTable.get(recSrcAdd) < recSeqNum||theRTable.get(recSrcAdd)>4090&&recSrcAdd>100){

								if(!theRTable.containsKey(recSrcAdd))
								{
									theRTable.put(recSrcAdd, (short) 0);
								}

								if(!(theRTable.get(recSrcAdd)==recSeqNum-1)||recSeqNum!=0)
								{
									LinkLayer.diagOut("There is a gap in the sequence numbers");
								}




								 byte[] theackpacket = new byte[10];
                                 theackpacket= BuildPacket.sixbytes(recPac);
                                 
                                  byte[] ackthing = BuildPacket.bitshift(ackshort);
                                  ackthing[0]=(byte)(ackthing[0] |(1<<5));
                                  byte[] theolddest= new byte[2];
                                  byte[] theoldsrc= new byte[2];
                                 byte[] thecrcarray= new byte[4];
                                 byte[] thetemparray = new byte[6];
                                 
                                  theolddest[0]=theackpacket[2];
                                 theolddest[1]= theackpacket[3];
                                  theoldsrc[0]=theackpacket[4];
                                 theoldsrc[1]= theackpacket[5];
                                 theackpacket = new byte[10];
                                theackpacket[0]=ackthing[0];
                                theackpacket[1]=ackthing[1];
                                theackpacket[2]=theolddest[0];
                                theackpacket[3]=theolddest[1];
                               theackpacket[4]=theoldsrc[0];
                               theackpacket[5]=theoldsrc[1];
                                
                                 System.arraycopy(theackpacket, 0, thetemparray, 0, 6);
                                 crcVal.update(thetemparray,0,thetemparray.length);
                                 thecrcarray = BuildPacket.bitshiftcrc(crcVal.getValue());
                                 theackpacket[6]=thecrcarray[0];
                                 theackpacket[7]=thecrcarray[1];
                                theackpacket[8]=thecrcarray[2];
                                theackpacket[9]=thecrcarray[3];



								LinkLayer.diagOut("it should be sending an ack");
								if(theRF.getIdleTime()<100){

									theRF.transmit(theackpacket);
									LinkLayer.diagOut("it should have sent an ack");
									BigInteger bi = new BigInteger(theackpacket);
									LinkLayer.diagOut(bi.toString(2));
									LinkLayer.diagOut("the address that it is from is "+BuildPacket.retSrcAd(theackpacket)+ "   the Adress it is to "+BuildPacket.retDestAd(theackpacket));
									if(recRetry==0)
									{
										short temp1 =theRTable.get(recSrcAdd);
										theRTable.remove(recSrcAdd);

										theRTable.put(recSrcAdd,(short) (temp1+1) );
									}


								}
								if(BuildPacket.rcvACK.get()){
									//ACK packet!
									//it is an ACK
									LinkLayer.diagOut("Received an ACK packet.");
									//NEED TO CLEAR OUT ARRAY BLOCKING QUEUE using SeqNum
								}
								if(BuildPacket.rcvBeacon.get()){
									//it is a Beacon packet
									System.out.println("Received a Beacon packet.");


								}
								if(BuildPacket.rcvCTS.get()){
									LinkLayer.diagOut("Received a CTS packet.");
								}
								if(BuildPacket.rcvRTS.get()){
									//it is an RTS
									LinkLayer.diagOut("Received a RTS packet.");
								}

								//if data packet, need to put into an A.B.Q. to be accessed in LinkLayer
								if(isData = true){
									//add the data to the ABQ
									recABQ.add(recData);
									dataInQ = true;
								}

								//reset booleans
								isData = false;
							}

						}
					}
					//reset for next recieved packet
					rcvTime = 0;

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


