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
	private ArrayBlockingQueue <byte []> ablockQf;
    
	
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
		ablockQf = abq;
		this.theRF=theRF;
	}
    
    
    
	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes.
	 */
	public void run() {
		System.out.println("send is alive and well");
		while(true){
			if(theRF.inUse()){}
			else{
                try {
                    
                    theRF.transmit(ablockQf.take());
                    System.out.print(" it should be sending for real");
                    
				}
                
                catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    System.out.println(e);
                    //e.printStackTrace();
                    
                }
			}
            
        }
        
        /*
         public void sender(byte[] buf, short destAddr, short sourceAddr) {
         
         packet=BuildPacket.build(buf, destAddr, sourceAddr);	
         //ablockQ.put(packet);
         //theRF.transmit(packet);
         }
         */
    }
}





