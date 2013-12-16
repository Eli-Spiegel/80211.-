package wifi;

import java.nio.ByteBuffer;
//import java.nio.ByteOrder; **************
import java.util.Arrays;
import java.util.HashMap;
//import java.util.concurrent.ArrayBlockingQueue; *****************
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
//import com.sun.org.apache.xalan.internal.xsltc.dom.BitArray; *****************

/**
 * The BuildPacket class prepares packets to be sent and also
 * pulls apart incoming packets. 
 * 
 * @author Eli Spiegel & Lauren Swanson
 * @version 12/15/2013
 *
 */
public class BuildPacket {
	public static String packet= null;
	//16 ones to be added twice for crc value
	static  Checksum crcVal = new CRC32();
	// used for sequence number
	static short buildcount = 0;
	//holds the received frame type
	static byte[] recFrameType = new byte [2];
	//holds the received retry bits
	static byte[] recRetry = new byte [2];
	//holds the received seq number
	static byte[] recSeqNum = new byte [2];
	//holds the received destination add
	static byte[] recDestAdd = new byte [2];
	//holds the received source add
	static byte[] recSrcAdd = new byte [2];
	//holds the received data in a byte array
	static byte[] recData = new byte [2038];
	//holds the received crc 
	static byte[] recCRC = new byte [4];
	//the following are shorts to be used elsewhere in
	//analyzing the pieces of an incoming packet 
	//static short shtRecData;  ****
	static short shtRecFrameType;
	static short shtRecRetry;
	static short shtRecSeqNum;
	static short shtRecDestAdd;
	static short shtRecSrcAdd;
	static short shtRecCRC;
	static short shtSendDestAdd;
	static short shtSendSeqNum;
	//these booleans are used in the receive thread to 
	//help determine what kind of packet was received
	static AtomicBoolean rcvData = new AtomicBoolean(false);
	static AtomicBoolean rcvACK = new AtomicBoolean(false);
	static AtomicBoolean rcvBeacon = new AtomicBoolean(false);
	static AtomicBoolean rcvCTS = new AtomicBoolean(false);
	static AtomicBoolean rcvRTS = new AtomicBoolean(false);
	static AtomicBoolean rcvRetry = new AtomicBoolean(false);
	//used to gather hold the first which holds the frame type and retry info
	static ByteBuffer firstByte = ByteBuffer.allocate(1);
	//a hash map used in keeping track of sequence numbers
	private static HashMap<Short,Short> theSTable = new HashMap<Short,Short>();

	/**
	 * Constructor for this BuildPacket class.
	 * The constructor doesn't perform any tasks, because
	 * all of the work is done by the following methods.
	 * 
	 * @param pac
	 */
	public BuildPacket(byte[] pac) {
		//dont use this. just call the individual methods
	}

	/**
	 * This method takes given data and addresses
	 * and build a packet with can then be sent.
	 * 
	 * @param data The byte array to be sent in the packet
	 * @param dest The destination address of the packet
	 * @param ourMac The source address of the packet (this MAC address)
	 * @param frameType: 0 = data, 8192 = ACK, 16384 = beacon, 32768 = CTS, 40960 = RTS
	 * 
	 * @return A packet which is ready to be transmitted
	 */
	public static byte[] build(byte[] data, short dest, short ourMac, short frameType) {

		//set the destination address
		shtSendDestAdd = dest;
		//check the sequence number table
		if( !theSTable.containsKey(dest))
		{
			//haven't sent here before, so 
			//seq num is zero
			theSTable.put(dest, (short)0);
		}
		//build could holds the sequence number for this packed=t
		buildcount= (Short) theSTable.get(dest);

		//add the sequence number and fill the rest in with zeros
		if(buildcount >= 4095){
			buildcount =0;
		}

		//"adding (+)" will AND the frame type into the first 2 bytes 
		//of the packet already containing the seq num
		shtSendSeqNum = (short) (buildcount + frameType);

		byte[] packet = bitshift(shtSendSeqNum); //put the seq num into a byte array
		byte[] destpac = bitshift(dest); //put dest address into a byte array
		byte[] mac = bitshift(ourMac);  // put our address into a byte array

		//begin putting the byte arrays together 
		byte[] packetFin = new byte[data.length+10];//the first concatenated byte array
		//add the bytes containing the frame type and seq num
		System.arraycopy(packet, 0, packetFin, 0, packet.length);
		//add the destination address
		System.arraycopy(destpac, 0, packetFin, 2, destpac.length);
		//add the source address
		System.arraycopy(mac, 0, packetFin,4, mac.length);
		//add the data
		System.arraycopy(data, 0, packetFin, 6, data.length);
		//get the CRC value
		crcVal.update(packetFin,0,packetFin.length-4);
		//add the CRC value to the packet
		System.arraycopy(bitshiftcrc(crcVal.getValue()), 0, packetFin, 6 + data.length, 4);

		//diagnostic outputs
		LinkLayer.diagOut("Your packet has been built and is ready to transmit.");
		LinkLayer.diagOut("Below shows what your built packet looks like: ");
		LinkLayer.diagOut(Arrays.toString(packetFin));

		//increment the sequence numbers
		theSTable.remove(dest);
		buildcount++; //increment the sequence number for this address
		theSTable.put(dest, (short)(buildcount));
		return packetFin; //return the completed packet to be transmitted
	}

	/**
	 * Used in adding the sequence number, destination 
	 * address and source address to a packet, by
	 * first putting it into a byte array
	 * @param theShort The sequence number
	 * @return A byte array containing the sequence number
	 */
	public static byte[] bitshift(short theShort ) {
		byte[] ret = new byte[2];
		ret[1] = (byte)(theShort & 0xff);
		ret[0]= (byte)((theShort >> 8)& 0xff);
		return ret;
	}

	/**
	 * 
	 * @param longs
	 * @return
	 */
	public static byte[] bitshiftcrc(long longs ) {
		byte[] bytes = new byte[4];
		bytes[3] = (byte)(longs & 0xff);
		bytes[2]= (byte)((longs >> 8)& 0xff);
		bytes[1]= (byte)((longs >> 16)& 0xff);
		bytes[0]= (byte)((longs >> 24)& 0xff);
		return bytes;
	}
	
	/**
	 * 
	 * @param longs
	 * @return
	 */
	public static byte[] bitshifttime(long longs ) {
		byte[] bytes = new byte[8];
		bytes[7] = (byte)(longs & 0xff);
		bytes[6] = (byte)((longs >> 8) & 0xff);
		bytes[5]= (byte)((longs >> 16)& 0xff);
		bytes[4]= (byte)((longs >> 24)& 0xff);
		bytes[3]= (byte)((longs >> 32)& 0xff);
		bytes[2]= (byte)((longs >> 40)& 0xff);
		bytes[1]= (byte)((longs >> 48)& 0xff);
		bytes[0]= (byte)((longs >> 56)& 0xff);
		return bytes;
	}


	/**
	 * Gets the frame type of a received packet.
	 * Data = 0, ACK = 32, Beacon = 64, CTS = 128, 
	 * RTS = 160. Also sets the atomic booleans for 
	 * the frame type.
	 * @param recData The entire received data packet
	 * @return The short value of the entire first byte
	 */
	public static short retFrameType(byte[] recData) {

		//make sure the booleans are set to zero
		rcvData.getAndSet(false);
		rcvACK.getAndSet(false);
		rcvBeacon.getAndSet(false);
		rcvRTS.getAndSet(false);
		
		//get the frame type from the packet
		recFrameType[0] = recData[0]; //frame type is in the first byte
		recFrameType[0] = (byte) (recFrameType[0] & 0xE0); //masking
		recFrameType[1] = 0; //fill in the other byte with zero
		
		//short that holds the value of the first byte
		shtRecFrameType = ByteBuffer.wrap(recFrameType).getShort();
		//should be holding the frame type followed by 13 zeros

		//Set the atomic booleans and give diagnostics.
		
		//Data: 00000000 = 0
		if(shtRecFrameType == (short)0){
			//then it is a data packet
			rcvData.getAndSet(true);
			LinkLayer.diagOut("BuildPacket - Recieved FrameType: Data");
		}
		//ACK =001000000000000000 =8192
		if(shtRecFrameType == (short)8192){
			//then it is an ACK packet
			rcvACK.getAndSet(true);
			LinkLayer.diagOut("BuildPacket - Recieved FrameType: ACK");
		}
		//Beacon = 0100000000000000 = 16384
		if(shtRecFrameType == (short)16384){
			//then it is a Beacon
			rcvBeacon.getAndSet(true);
			LinkLayer.diagOut("BuildPacket - Recieved FrameType: Beacon");
		}
		//CTS = 10000000000000000 = 32768
		if(shtRecFrameType == (short)32768){
			//then it is a CTS
			rcvCTS.getAndSet(true);
			LinkLayer.diagOut("BuildPacket - Recieved FrameType: CTS");
		}
		//RTS = 1010000000000000 = 40960
		if(shtRecFrameType == (short)40960){
			//then it is a RTS
			rcvRTS.getAndSet(true);
			LinkLayer.diagOut("BuildPacket - Recieved FrameType: RTS");
		}
		//return the frame type to be used by the threads
		return shtRecFrameType;
	}

	/**
	 * Used to get the retry value from the 
	 * incoming data
	 * @param recData The whole incoming packet
	 * @return The short representation of the Retry
	 */
	public static short retRetry(byte[] recData){
		//make sure the boolean is set to zero
		rcvRetry.getAndSet(false);
		//get the bytes containing the retry
		recRetry[0] = recData[0];
		recRetry[1] = recData[1];
		//short that holds the value of the first byte
		shtRecRetry = ByteBuffer.wrap(recRetry).getShort();
		//AND with 00010000, short value of 4096
		shtRecRetry = (short)(shtRecRetry & (short)(4096));
		//should be holding the retry bit, with 3 zeros in front and 4 behind

		//set the boolean and report
		if(shtRecRetry > 0){
			rcvRetry.getAndSet(true);
			LinkLayer.diagOut("BuildPacket - The RETRY bit is 1.");
		}else{
			//not a retry
			LinkLayer.diagOut("BuildPacket - The RETRY bit is 0.");
		}

		return shtRecRetry;//return the retry value
	}

	/**
	 * Gets the sequence number from the incoming packet.
	 * @param recData The entire incoming packet.
	 * @return The short value of the sequence number.
	 */
	public static short retSeqNum(byte[] recData){
		//move the first two bytes into the array
		recSeqNum[0] = recData[0];
		recSeqNum[1] = recData[1];
		//short that holds the value of the first byte
		shtRecSeqNum = ByteBuffer.wrap(recSeqNum).getShort();
		//AND with 0000111111111111, (short value = 4095)
		shtRecSeqNum = (short)(shtRecSeqNum & (short)4095);
		//should be holding the sequence number, 4 zeros in front
		LinkLayer.diagOut("BuildPacket - The Sequence Number is: " + shtRecSeqNum);
		return shtRecSeqNum;
	}

	/**
	 * Gets the destination address from the incoming packet.
	 * @param recData The entire incoming packet
	 * @return The short representation of the packet's
	 * destination address.
	 */
	public static short retDestAd(byte[] recData){
		//dest address is two bytes in
		recDestAdd[0] = recData[2];
		recDestAdd[1] = recData[3];
		//get the short value of these bytes
		shtRecDestAdd = ByteBuffer.wrap(recDestAdd).getShort();
		//shtRecData = ByteBuffer.wrap(recDestAdd).getShort();*******
		return shtRecDestAdd;
	}

	/**
	 * Gets the source address from the incoming packet.
	 * @param recData The entire incoming packet
	 * @return The short representation of the packet's
	 * source address.
	 */
	public static short retSrcAd(byte[] recData){
		//src address is four bytes in
		recSrcAdd[0] = recData[4];
		recSrcAdd[1] = recData[5];
		//get the short value of these bytes
		shtRecSrcAdd = ByteBuffer.wrap(recSrcAdd).getShort();
		return shtRecSrcAdd;
	}

	/**
	 * Gets the data from the incoming packet.
	 * If it is a data packet, with will be data.
	 * If it is a beacon packet, this will be a timestamp.
	 * @param recData The entire incoming packet
	 * @return The data or timestamp contained in the data.
	 */
	public static byte[] retRecData(byte[] recData){
		//data is in bytes 6 till end -4
		byte [] rcvData = new byte[recData.length-10];
		System.arraycopy(recData, 6, rcvData, 0, recData.length-10);
		return rcvData;//return this byte array
	}

	/**
	 * Gets the CRC from the incoming packet.
	 * @param recData The entire incoming packet
	 * @return The short representation of the packet's CRC value.
	 */
	public static short retCrc(byte[] recData){
		//CRC is in last 4 bytes
		int byteCounter = (recData.length - 4); //start at beginning of data
		int addCounter = 0;
		while(byteCounter<(recData.length) && addCounter<4){
			byte by = recData[byteCounter];
			recCRC[addCounter] = by;
			byteCounter = byteCounter +1;
			addCounter = addCounter +1;
		}
		shtRecCRC = ByteBuffer.wrap(recCRC).getShort();
		return shtRecCRC;
	}

	/**
	 * Gets the first 6 bytes out of the incoming packet,
	 * to be used in the receive thread for building an
	 * ACK packet.
	 * @param recData The entire incoming packet
	 * @return The byte array holding the first 6 bytes.
	 */
	public static byte[] sixbytes(byte[] recData)
	{
		byte[] ret = new byte [6];
		//gather the first 6 bytes
		ret[0]= recData[0];
		ret[1]=recData[1];
		ret[2]=recData[2];
		ret[3]=recData[3];
		ret[4]= recData[4];
		ret[5]=recData[5];

		return ret;

	}

}





