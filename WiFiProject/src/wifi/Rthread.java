package wifi;

import java.lang.reflect.Array; 
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean; 
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import rf.RF;

/**
 * This is a Thread that runs and checks for incoming
 * packets and handles the decomposition of the packet
 * contents.
 * @author Eli Spiegel & Lauren Swanson
 * @version 12/15/2013
 */
public class Rthread implements Runnable {
	private  byte[] recPac;
	private  RF theRF;
	private ArrayBlockingQueue <byte []> ACKablockQ = new ArrayBlockingQueue<byte []>(10); 
	private short recDestAdd;
	private short recSrcAdd;
	private short recFrameType;
	private short recRetry;
	private short recSeqNum;
	private byte[] recData;
	private short recCRC; 
	private boolean isData = false;
	public static long rcvTime;//static to be used in send thread
	private ArrayBlockingQueue <byte []> recABQ = new ArrayBlockingQueue<byte []>(10);
	private short ourMAC;
	boolean dataInQ = false;
	public short ackshort = 8192;
	private HashMap<Short,Short> theRTable= new HashMap<Short,Short>();
	Checksum crcVal = new CRC32();
	//for the timing fudge factor
	static AtomicLong fudge = new AtomicLong();

	/**
	 * This constructor is assigns variable to be used 
	 * in handling incoming packets.
	 * @param packet The entire incoming packet
	 * @param ourAdd Our own address
	 * @param theRF The RF layer being used
	 * @param abq The Array Blocking Queue holding received packets.
	 */
	public Rthread(byte[]packet, short ourAdd, RF theRF, ArrayBlockingQueue abq)
	{
		recABQ = abq; //will put received data here
		ourMAC = ourAdd;
		this.theRF = theRF;
		recPac = packet; 
		rcvTime = 0; // start at 0 until we receive something 
	}


	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes. 
	 */
	public void run() {
		LinkLayer.diagOut("Receive Thread is alive and well.");
		while(true)
		{
			//see if there is incoming data for us in the RF layer
			if(theRF.dataWaiting()){
				//there stuff for us! 
				recPac = theRF.receive();//Make a call to receive!
				
				//did we actually get a packet with stuff in it?
				if (recPac.length != 0){
					//Yep! We need to figure out what it is.
					rcvTime = theRF.clock()+fudge.get();//set the received time
					//print the time it was received
					LinkLayer.diagOut("Recieve Thread - Recieved a packet at: "+rcvTime);
					
					//check if it is for us
					recDestAdd = BuildPacket.retDestAd(recPac);
					recSrcAdd = BuildPacket.retSrcAd(recPac);
					LinkLayer.diagOut("Recieve Thread - Incoming packet from:" + recSrcAdd);
					
					if(recDestAdd == ourMAC || recDestAdd == -1){ //it's for us or a beacon!

						//each time this is called it starts with all false booleans for frame types 
						recFrameType = BuildPacket.retFrameType(recPac);
						recRetry = BuildPacket.retRetry(recPac);
						recSeqNum = BuildPacket.retSeqNum(recPac);
						recData = BuildPacket.retRecData(recPac);
						//diagnostics
						LinkLayer.diagOut("Recieve Thread - The incoming packet's desination address is " +recDestAdd);
						LinkLayer.diagOut("Recieve Thread - The packet is for us!");
						//check what type of packet we are receiving

						//is it a beacon?
						if(recFrameType == 16384){ //yep!
							//get 8-14 bytes
							byte [] timeStamp = BuildPacket.retRecData(recPac);
							ByteBuffer buf = ByteBuffer.wrap(timeStamp);
							//make sure we are at the start of the buffer
							buf.rewind();
							//get the long value of the timestamp
							Long btime = buf.getLong();
							//should we update our clock or just send a response beacon?
							if( btime< (theRF.clock()+fudge.get())){
								//send beacon
								buf.clear();//reset the buffer for timestamps
								//build our beacon
								byte[] ourtime =BuildPacket.bitshifttime((theRF.clock()+(long)9010 + Rthread.fudge.get()));
								byte[] beacon = BuildPacket.build(ourtime,(short) -1, LinkLayer.ourMAC, (short)16384);
								theRF.transmit(beacon); //send the beacon
								LinkLayer.diagOut("Receive Thread - The timestamp in the BEACON is "+btime);
								LinkLayer.diagOut("Receive Thread - Sending a response beacon. We are ahead of them.");
							}
						
							if(btime > theRF.clock()+fudge.get()){
								//we're behind them
								//update fudge factor
								fudge.set((long)( btime - theRF.clock()));
								LinkLayer.diagOut("Receive Thread - The timestamp in the BEACON is "+btime);
								LinkLayer.diagOut("Receive Thread  - Updated our fudge factor to the beacon's time. They are ahead of us.");
							}

						}

						//what other type of packet could it be?

						if(recFrameType==0){
							//data packet!
							isData = true;
							LinkLayer.diagOut("Receive Thread - Received a data packet.");

							//receiving from a new address?
							if(!theRTable.containsKey(recSrcAdd))
							{
								//we haven't see this address yet so 
								//add it and start seq num at 0
								theRTable.put(recSrcAdd, (short) 0);
							}
							//receiving from an old address? 
							if(theRTable.get(recSrcAdd) <= recSeqNum || ((theRTable.get(recSrcAdd)>4090 && (recSeqNum<10)))){

								if(!(theRTable.get(recSrcAdd)==recSeqNum-1) && recSeqNum!=0)
								{
									//sequence numbers are out of order
									LinkLayer.diagOut("Receive Thread - There is a gap in the sequence numbers!");
								}
								
								if(isData = true){
									//add the data to the ABQ
									recABQ.add(recData);
									dataInQ = true;
								}
								
								//prepare the ack packet
								byte[] theackpacket = new byte[10];
								theackpacket= BuildPacket.sixbytes(recPac); //get the addresses and such
								byte[] ackthing = BuildPacket.bitshift(ackshort);
								ackthing[0]=(byte)(theackpacket[0] |(1<<5));
								ackthing[1]=theackpacket[1];
								byte[] theolddest= new byte[2];
								byte[] theoldsrc= new byte[2];
								byte[] thecrcarray= new byte[4];
								byte[] thetemparray = new byte[6];


								theoldsrc[0]=theackpacket[2];
								theoldsrc[1]= theackpacket[3];
								theolddest[0]=theackpacket[4];
								theolddest[1]= theackpacket[5];
								theackpacket = new byte[10];
								theackpacket[0]=ackthing[0];
								theackpacket[1]=ackthing[1];
								theackpacket[2]=theolddest[0];
								theackpacket[3]=theolddest[1];
								theackpacket[4]=theoldsrc[0];
								theackpacket[5]=theoldsrc[1];
								//now copy into the temporary array
								System.arraycopy(theackpacket, 0, thetemparray, 0, 6);
								//update the CRC
								crcVal.update(thetemparray,0,thetemparray.length);
								thecrcarray = BuildPacket.bitshiftcrc(crcVal.getValue());
								//add the CRC to the ACK packet
								theackpacket[6]=thecrcarray[0];
								theackpacket[7]=thecrcarray[1];
								theackpacket[8]=thecrcarray[2];
								theackpacket[9]=thecrcarray[3];

								LinkLayer.diagOut("Receive Thread - Built the ACK and is now sending it");
								if(theRF.getIdleTime()<100){
									//The RF is available
									theRF.transmit(theackpacket); //send the ACK
									LinkLayer.diagOut("Receive Thread - Sent the ACK to "+BuildPacket.retSrcAd(theackpacket));
									//remove the now ACKed packet from our sequence number table
									if(recRetry==0)
									{ //this was the first time we saw the packet
										short temp1 =theRTable.get(recSrcAdd);
										theRTable.remove(recSrcAdd);
										theRTable.put(recSrcAdd,(short) (temp1+1) );
									}
								}
								
								
								if(BuildPacket.rcvACK.get()){
									//ACK packet!
									LinkLayer.diagOut("Receive Thread - Received an ACK packet.");
								}
								if(BuildPacket.rcvCTS.get()){
									LinkLayer.diagOut("Received a CTS packet.");
								}
								if(BuildPacket.rcvRTS.get()){
									//it is an RTS
									LinkLayer.diagOut("Received a RTS packet.");
								}

								//if data packet, need to put into an A.B.Q. to be accessed in LinkLayer
								
								//reset booleans
								isData = false;
							}
						}
					}
					//reset for next received packet
					rcvTime = 0;

				}
			}
		}
	}

	/**
	 * A getter for the Link Layer return the first
	 * byte array in the received Array Blocking Queue 
	 * and removes it from the front.
	 * @return The byte array of the entire received packet.
	 */
	public byte[] getRecABQ(){
		byte[] toPass = null;
		try {
			toPass = recABQ.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			//update status
			LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
		}
		return toPass;
	}

	/**
	 * Used in the Thread to see if there is data
	 * to be received. Resets every time so it is
	 * current.
	 * @return True if data waiting. False if no
	 * data waiting. 
	 */
	public boolean dataWaitinginQ(){

		if(dataInQ){
			dataInQ = false;//will be empty once removed
			return true;
		}

		return false;
	}

	/**
	 * Gets the data from the ArrayBlocking Queue. To be
	 * used by the Link Layer. 
	 * @return Byte Array of the incoming data packet
	 */
	public byte[] getData(){
		//
		try {
			return recABQ.take();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			//update status
			LinkLayer.setStatus.set(2);//UNSPECIFIED ERROR
		}
		return null; //if things didn't work
	}

	/**
	 * Getter for other Classes.
	 * @return Short value of the destination address of 
	 * the received packet
	 */
	public short getDestAdd(){
		return recDestAdd;
	}

	/**
	 * Getter for other Classes.
	 * @return Short value of the source address of 
	 * the received packet
	 */
	public short getSrcAdd(){
		return recSrcAdd;
	}

}


