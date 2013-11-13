package wifi;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import rf.RF;


public class send implements Runnable {

	private  byte[] packet;
	private  RF theRF;

	/**
	 * Each grunt gets "personalized" with these argument values.
	 * @param id The id number of this specific Grunt object.
	 * @param time Used in the run() method to figure out when to print.
	 */

	public send(byte[]packet, short dest, RF theRF)
	{

	}
		
	
	
	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes. 
	 */
	public void run() {
		System.out.println("send is alive and well");
		
		
		theRF.transmit(packet);
		}
	

	public void sender(byte[] packet1) {
		packet=packet1;	
		run();
	}
}
