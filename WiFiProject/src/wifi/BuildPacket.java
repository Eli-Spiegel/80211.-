package wifi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.sun.org.apache.xalan.internal.xsltc.dom.BitArray;

public class BuildPacket {
	public static String packet= null;
	//16 ones to be added twice for crc value
	static String crcVal = "0000000000000000";
	// used for sequence number
	static int buildcount = 0;
	//holds the received frame type
	static byte[] recFrameType;
	//holds the received retry bits
	static byte[] recRetry;
	//holds the received seq number
	static byte[] recSeqNum;
	//holds the received destination add
	static byte[] recDestAdd;
	//holds the received source add
	static byte[] recSrcAdd;
	//holds the received data in a byte array
	static byte[] recData;
	//holds the received crc 
	static byte[] recCRC;
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

		shred(pac);

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
	 * Saves each piece of the received packet
	 * into separate byte arrays.
	 * These can be accessed through helper methods.
	 * @param pa received packet
	 */
	public static void shred(byte[] pa) {
		int index = 0;
		int i = 0;
		byte b = pa[index];

		//go till end of frameType bits
		if (i < 3) {
			int frameTypeCounter = 0;
			while (i < 3) {
				// looking at the frame type
				b = (byte) (b >>> 1);
				//save bits 0-2 in byte array
				recFrameType[frameTypeCounter] = b;
				//if (b != 0 && i == 2) {
				// then ACK?
				//shredack();
				//} else {
				if (b != 0) {
					System.out.println("Not receiving data. Other info or corrupt.");
				}
				//}

				System.out.println("B: " + b);
				i++;
				frameTypeCounter = frameTypeCounter +1;
			}
		}
		
		if (i==3){   //at retry bit
			b = (byte) (b >>> 1);
			//save the retry bit. There will only be one
			recRetry[0] = b;
			i++;
		}
		//used in saving the SeqNum bits
		int seqCounter = 0;
		//go to end of SeqNum bits
		while (i < 15) {
			//we need to got to next spot in byte array
			if (i == 8) {
				//next position in byte array
				b = pa[index + 1];
			}
			recSeqNum[seqCounter] = (byte) (b >>> 1);
			//to step through received packet
			i++;
			//so we can add the next byte into the recSeqNum array
			seqCounter = seqCounter +1;
		}

		//go to next byte in array (we have parsed 16-bits so far)
		b = pa[index + 1];
		int destAddCounter = 0;
		// for dest address from 16-31 bits
		while (i < 32) {
			//check if we need to move to next byte
			if (i % 8 == 0) {
				//move to next byte
				b = pa[index + 1];
			}
			recDestAdd[destAddCounter] = (byte) (b >>> 1);
			destAddCounter = destAddCounter +1;
			i = i + 1;
		}
		int srcAddCounter = 0;
		// for source address 32-47 bits
		while (i < 48) {
			//check if we need to move to next byte
			if (i % 8 == 0) {
				//move to next byte
				b = pa[index + 1];
			}
			recSrcAdd[srcAddCounter] = (byte) (b >>> 1);
			srcAddCounter = srcAddCounter + 1;
			i = i + 1;
		}

		int dataAddCounter = 0;
		// Now need keep data (the remaining bytes-4 for CRC)
		while (index < pa.length - 4) {
			//do we need to move to next byte
			if (i % 8 == 0) {
				//move to next byte
				b = pa[index + 1];
			}
			//save the received data
			recData[dataAddCounter] = (byte) (b >>> 1);
			dataAddCounter = dataAddCounter +1;
			i = i+1;
		}

		int crcAddCounter = 0;
		// Now get check sum
		while (index < pa.length) {
			//do we need to move to next byte
			if (i % 8 == 0) {
				//move to next byte
				b = pa[index + 1];
			}
			recCRC[crcAddCounter] = (byte) (b >>> 1);
			crcAddCounter = crcAddCounter + 1;
			i = i + 1;
		}
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
		int byteCounter = 2;
		int addCounter = 0;
		while(byteCounter < 5){
			//recDestAdd[addCounter] = recData[byteCounter];
			byte by = recData[byteCounter];
			recDestAdd[addCounter] = by;
			byteCounter = byteCounter +1;
			addCounter = addCounter +1;
		}
		
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
		int byteCounter = 4;
		int addCounter = 0;
		while(byteCounter < 7){
			byte by = recData[byteCounter];
			recSrcAdd[addCounter] = by;
			byteCounter = byteCounter +1;
			addCounter = addCounter +1;
		}
		
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
		while(byteCounter < (recData.length)){
			byte by = recData[byteCounter];
			recCRC[addCounter] = by;
			byteCounter = byteCounter +1;
			addCounter = addCounter +1;
		}
		shtRecCRC = ByteBuffer.wrap(recCRC).order(ByteOrder.LITTLE_ENDIAN).getShort();
		return shtRecCRC;
	}

}


