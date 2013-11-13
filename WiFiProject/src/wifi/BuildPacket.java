package wifi;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.sun.org.apache.xalan.internal.xsltc.dom.BitArray;

public class BuildPacket {
	//used for sequence number
	static int buildcount=0;
	//16 ones to be added twice for crc value
	static String crcVal = "0000000000000000";
	public static String packet= null;
	public BuildPacket( byte[] pac){
		shred(pac);
		
	}
	public  void BuildPacket(byte[] data, short dest, short ourMac){
		
		
		
	}
	
	public static byte[] build(byte[] data, short dest, short ourMac) {
		
		//make seq length 12 bits
		String seq = Integer.toBinaryString(buildcount);
		int length = seq.length();
		//add the sequence number and fill the rest in with zeros
		if(length > 11){
			//sequence num is too great
			//set to zero 
			seq = "000000000000";
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
		byte[] packetFin = new byte[packet.length + destpac.length+mac.length+crc.length+crc.length+data.length];
		//add the bytes containing the frame type, retry, and seq num
		System.arraycopy(packet, 0, packetFin, 0, packet.length);
		//add the destination byte
		System.arraycopy(destpac, 0, packetFin, packet.length, destpac.length);
		//add the source address
		System.arraycopy(mac, 0, packetFin, packet.length+destpac.length, mac.length);
		//add the data
		System.arraycopy(data, 0, packetFin, packet.length, data.length);
		//add the first crc
		System.arraycopy(crc, 0, packetFin, packet.length+destpac.length+mac.length, crc.length);
		//add the second crc
		System.arraycopy(crc, 0, packetFin, packet.length+destpac.length+mac.length+crc.length, crc.length);
		System.out.println("it is doing this /n");
		System.out.println(Arrays.toString(packetFin));
		
		
		
		/*
		 *  
		//the second concatenated byte array (adds the source address)
		byte[] packetCatb = new byte[packetCata.length + mac.length];
		//add the first concatenated packet
		System.arraycopy(packetCata, 0, packetCatb, 0, packetCata.length);
		//add the source address
		System.arraycopy(mac, 0, packetCatb, packetCata.length, mac.length);
		
		//the third concatenated byte array (adds the data)
		byte[] packetCatc = new byte[packetCatb.length + data.length];
		//add the second concatenated packet
		System.arraycopy(packetCatb, 0, packetCatc, 0, packetCatb.length);
		//add the data to the final byte array
		System.arraycopy(data, 0, packetCatc, packetCatb.length, data.length);
		
		//the fourth concatenated byte array (add the CRC)
		byte[] packetCatd = new byte[packetCatc.length + crc.length];
		//add the third concatenated packet
		System.arraycopy(packetCatc, 0, packetCatd, 0, packetCatc.length);
		//add the crc to the end
		System.arraycopy(crc, 0, packetCatd, packetCatc.length, crc.length);
		
		//the final concatenated byte array (add the second CRC)
		byte[] packetFin = new byte[packetCatd.length + crc.length];
		//add the third concatenated packet
		System.arraycopy(packetCatd, 0, packetFin, 0, packetCatd.length);
		//add the crc to the end
		System.arraycopy(crc, 0, packetFin, packetCatd.length, crc.length);
		
		//the final packet will contain the frame type, retry, seq num, destAddr,
		//srcAddr, data, and crc
		*/
		
		buildcount++;
		return packetFin;
		
		
	}
	public BitArray shred(byte[] pa){
		int index=0;
		int i =0;
		byte b= pa[index];
		if(i<4){
		while( i < 3)
		{
		    b = (byte) (b >>> 1);
		    if(b!=0 && i==2){
		    	shredack();
		    	}
		    else{
		    	if(b!=0){
		    		System.out.println("fail!");
		    	}
		    }
		    
		    System.out.println("B: " + b);
		    i++;
		}
		}else{
		int retry =  b = (byte) (b >>> 1);
		i++;
		while( i <16 ){
			if(i ==7)
			{
				b=pa[index +1];
			}
			 int sequence = (byte) (b >>> 1);
			 i++;
		}
		}
		b = pa[index +1];
		//for dest address
		while(i<64){
			if (i%8 == 0){
				b = pa[index +1];
			}
			int destAdd = (byte) (b >>> 1);
		}
		//for source address
		while(i<96){
			if (i%8 == 0){
				b = pa[index +1];
			}
			int srcAdd = (byte) (b >>> 1);
		}
		
		//Now need keep data
		while(index < pa.length-4){
			//store
			if (i%8 == 0){
				b = pa[index +1];
			}
			int data = (byte) (b >>> 1);
		}
		//Now get check sum
		while(index < pa.length)
		{
			//store
			if (i%8 == 0){
				b = pa[index +1];
			}
			int crc = (byte) (b >>> 1);
		}
		
		return null;
		
	}
	private void shredack() {
		// TODO Auto-generated method stub
		
	}

		/*
		 * private static byte[] bitshift(short a)
	{
		byte msb= (byte)(a>>> 8);  
		byte lsb = (byte)a;
		byte [] packet= {msb, lsb};
	
		
		
		return packet;
		}
		 */


	public static byte[] bitshift(short theShort ) {
		 String bytess = "";
		 // test each bit (all 32 of them) for( int i = 31; i >= 0; i-- ) {
		 for( int i = 16; i >= 0; i-- ) {
			 if( (theShort & (1 << i)) != 0 ) {
			 bytess += "1"; // the ith bit is set
			 } else {
			 bytess += "0"; // the ith bit is NOT set
			 }
			 }
		 short a = Short.parseShort(bytess, 2);
		 ByteBuffer bytes = ByteBuffer.allocate(2).putShort(a);

		 byte[] array = bytes.array();
			 return array;
		 }
}



