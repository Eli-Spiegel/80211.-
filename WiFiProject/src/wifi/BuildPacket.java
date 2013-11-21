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
	static byte[] recFrameType = new byte [3];
	//holds the received retry bits
	static byte[] recRetry = new byte [1];
	//holds the received seq number
	static byte[] recSeqNum = new byte [12];
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
		byte[] destpac = bitshift(ourMac);
		byte[] mac = bitshift(dest); 
		byte[] crc = bitshift(crcval);
	
		//the first concatenated byte array 
		byte[] packetFin = new byte[packet.length +packet.length+ destpac.length+mac.length+crc.length+crc.length+data.length];
		//add the bytes containing the frame type, retry, and seq num
		System.arraycopy(packet, 0, packetFin, 0, packet.length);
		System.arraycopy(packet, 0, packetFin, packet.length, packet.length);
		//add the destination byte
		System.arraycopy(destpac, 0, packetFin, packet.length+packet.length, destpac.length);
		//add the source address
		System.arraycopy(mac, 0, packetFin, packet.length+packet.length+destpac.length, mac.length);
		//add the data
		System.arraycopy(data, 0, packetFin, packet.length+packet.length+destpac.length+mac.length, data.length);
		//add the first crc
		System.arraycopy(crc, 0, packetFin, packet.length+packet.length+destpac.length+mac.length+data.length, crc.length);
		//add the second crc
		System.arraycopy(crc, 0, packetFin, packet.length+packet.length+destpac.length+mac.length+data.length+crc.length, crc.length);
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
	 * Gets the frame type of a received packet
	 * @return
	 */
	public static short retFrameType(byte[] recData) {
		
		
		return shtRecFrameType;
	}

	/**
	 * 
	 * @return
	 */
	public static short retRetry(byte[] recData){
		
		return shtRecRetry;
	}

	/**
	 * 
	 * @return
	 */
	public static short retSeqNum(byte[] recData){
		
		return shtRecSeqNum;
	}

	/**
	 * 
	 * @param
	 * @return
	 */
	public static short retDestAd(byte[] recData){
		//dest address is two bytes in
		recDestAdd[0] = recData[3];
		recDestAdd[1] = recData[2];

		shtRecData = ByteBuffer.wrap(recDestAdd).order(ByteOrder.LITTLE_ENDIAN).getShort();
		
		return shtRecData;
	}

	/**
	 * 
	 * @param
	 * @return
	 */
	public static short retSrcAd(byte[] recData){
		//src address is four bytes in
		recSrcAdd[0] = recData[5];
		recSrcAdd[1] = recData[4];
	
		shtRecSrcAdd = ByteBuffer.wrap(recSrcAdd).order(ByteOrder.LITTLE_ENDIAN).getShort();
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
		
		//add counter*8 is the number of bits received.
		
		//shtRecData = ByteBuffer.wrap(recData).order(ByteOrder.LITTLE_ENDIAN).getShort();
		
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
		shtRecCRC = ByteBuffer.wrap(recCRC).order(ByteOrder.LITTLE_ENDIAN).getShort();
		return shtRecCRC;
	}

}


