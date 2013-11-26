package wifi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;


import com.sun.org.apache.xalan.internal.xsltc.dom.BitArray;

public class BuildPacket {
	public static String packet= null;
	//16 ones to be added twice for crc value
	static String crcVal = "0000000000000000";
	// used for sequence number
	static int buildcount = 0;
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
	//the following are shorts to be used elsewhere
	static short shtRecData;
	static short shtRecFrameType;
	static short shtRecRetry;
	static short shtRecSeqNum;
	static short shtRecDestAdd;
	static short shtRecSrcAdd;
	static short shtRecCRC;
	static boolean rcvData;
	static boolean rcvACK;
	static boolean rcvBeacon;
	static boolean rcvCTS;
	static boolean rcvRTS;
	static boolean rcvRetry;
	
	static ByteBuffer firstByte = ByteBuffer.allocate(1);

	/**
	 * Constructor for taking apart a packet received from Wifi
	 * 
	 * @param pac
	 */
	public BuildPacket(byte[] pac) {
		//dont use this. just call the individual methods


	}

	/**
	 * Constructor for building a packet to send to Wifi
	 * 
	 * @param data
	 * @param dest
	 * @param ourMac
	 */
	public BuildPacket(byte[] data, short dest, short ourMac) {

	}

	/**
	 * Takes given data and addresses and builds a sendable packet
	 * 
	 * @param data
	 * @param dest
	 * @param ourMac
	 * @return
	 */
public static byte[] build(byte[] data, short dest, short ourMac) {
		
		//make seq length 12 bits
		String seq = Integer.toBinaryString(buildcount);
		int length = seq.length();
		//add the sequence number and fill the rest in with zeros
		if(length > 11){
			//sequence num is too great
			//set to zero 
			seq = "0000";
			length = seq.length();
		}
			while (length < 12)
			{
				seq = "0" + seq;
				length = length +1;
			}
		//adds "000" for data type and "0" for retry	
		seq= "0000"+seq;
		short pac = Short.parseShort(seq,2);
		short crcval= Short.parseShort(crcVal, 2);
		
		
		  byte[] packet = bitshift(pac);
		byte[] destpac = bitshift(dest);
		byte[] mac = bitshift(ourMac); 
		byte[] crc = bitshift(crcval);
	
		//the first concatenated byte array 
		byte[] packetFin = new byte[packet.length +packet.length+ destpac.length+mac.length+crc.length+crc.length+data.length];
		//add the bytes containing the frame type, retry, and seq num
		
		System.arraycopy(packet, 0, packetFin, packet.length, packet.length);
		//add the destination byte
		System.arraycopy(destpac, 0, packetFin, packet.length, destpac.length);
		//add the source address
		System.arraycopy(mac, 0, packetFin,packet.length+destpac.length, mac.length);
		//add the data
		System.arraycopy(data, 0, packetFin, packet.length+destpac.length+mac.length, data.length);
		//add the first crc
		System.arraycopy(crc, 0, packetFin,packet.length+destpac.length+mac.length+data.length, crc.length);
		//add the second crc
		System.arraycopy(crc, 0, packetFin, packet.length+destpac.length+mac.length+data.length+crc.length, crc.length);
		System.out.println("it is doing this /n");
		System.out.println(Arrays.toString(packetFin));
		
		
		buildcount++;
		return packetFin;
		
	}

/**
 * 
 * @param theShort
 * @return
 */
public static byte[] bitshift(short theShort ) {
	byte[] ret = new byte[2];
	ret[1] = (byte)(theShort & 0xff);
	ret[0]= (byte)((theShort >> 8)& 0xff);
	return ret;
}

	


	/**
	 * Gets the frame type of a received packet.
	 * The short value of the entire first byte is returned
	 * Data = =
	 * ACK = 32
	 * Beacon = 64
	 * CTS = 128
	 * RTS = 160
	 * Set booleans for isData, isACK, etc.
	 * @return
	 */
	public static short retFrameType(byte[] recData) {
	
		//make sure the booleans are set to zero
		rcvData = false;
		rcvACK = false;
		rcvBeacon = false;
		rcvCTS = false;
		rcvRTS = false;
		recFrameType[0] = recData[0];
		recFrameType[1] = recData[1];
		//short that holds the value of the first byte
		shtRecFrameType = ByteBuffer.wrap(recFrameType).getShort();
		//AND with 11100000, short value of 75344
		shtRecFrameType = (short)(shtRecFrameType & 0xE000);
		//should be holding the frame type followed by 5 zeros
		
		//Data: 00000000 = 0
		if(shtRecFrameType == 0){
			//then it is a data packet
			rcvData = true;
		}
		//ACK = 00100000 = 32
		if(shtRecFrameType == 32){
			//then it is an ACK packet
			rcvACK = true;
		}
		//Beacon = 01000000 = 64
		if(shtRecFrameType == 64){
			//then it is a Beacon
			rcvBeacon = true;
		}
		//CTS = 10000000 = 128
		if(shtRecFrameType == 128){
			//then it is a CTS
			rcvCTS = true;
		}
		//RTS = 10100000 = 160
		if(shtRecFrameType == 160){
			//then it is a RTS
			rcvRTS = true;
		}
		System.out.println("The FrameType is : " + shtRecFrameType);
		return shtRecFrameType;
	}

	/**
	 * 
	 * @return
	 */
	public static short retRetry(byte[] recData){
		//make sure the boolean is set to zero
		rcvRetry = false;
		recRetry[0] = recData[0];
		recRetry[1] = recData[1];
		//short that holds the value of the first byte
		shtRecRetry = ByteBuffer.wrap(recRetry).getShort();
		//AND with 00010000, short value of 4096
		shtRecRetry = (short)(shtRecRetry & (short)(4096));
		//should be holding the retry bit, with 3 zeros in front and 4 behind
		
		System.out.println("The Retry Bit is : " + shtRecRetry);
		
		//set the boolean
		if(shtRecRetry > 0){
			rcvRetry = true;
		}
		
		return shtRecRetry;
	}

	/**
	 * 
	 * @return
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
		System.out.println("The Sequence Number is: " + shtRecSeqNum);
		return shtRecSeqNum;
	}

	/**
	 * 
	 * @param
	 * @return
	 */
	public static short retDestAd(byte[] recData){
		//dest address is two bytes in
		recDestAdd[0] = recData[2];
		recDestAdd[1] = recData[3];

		shtRecData = ByteBuffer.wrap(recDestAdd).getShort();
		
		return shtRecData;
	}
	
	/**
	 * 
	 * @param
	 * @return
	 */
	public static short retSrcAd(byte[] recData){
		//src address is four bytes in
		recSrcAdd[0] = recData[4];
		recSrcAdd[1] = recData[5];
	
		shtRecSrcAdd = ByteBuffer.wrap(recSrcAdd).getShort();
		return shtRecSrcAdd;
	}

	/**
	 * @param 
	 * @return
	 */
	public static byte[] retRecData(byte[] recDataP){
		//data is in bytes 6 till end -4
		int byteCounter = 6; //start at beginning of data
		int addCounter = 0;
		while(byteCounter < (recData.length-4)){
			byte by = recData[byteCounter];
			recData[addCounter] = by;
			byteCounter = byteCounter +1;
			addCounter = addCounter +1;
		}
		
		return recData;
	}

	/**
	 * 
	 * @param
	 * @return
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
	
	public static byte[] sixbytes(byte[] recData)
	{
		byte[] ret = new byte [6];
		 ret[0]= recData[0];
		 ret[1]=recData[1];
		 ret[2]=recData[2];
		 ret[3]=recData[3];
		 ret[4]= recData[4];
		 ret[5]=recData[5];
	
		 
		 return ret;
		 
	}




}



