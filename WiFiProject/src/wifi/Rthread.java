import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

/**
 * 
 * @author lswanson
 *
 */
public class Rthread implements Runnable {
	private  byte[] packet;
	private  RF theRF;
	private ArrayBlockingQueue <byte []> ablockQ;
	
	
	/**
	 * A second constructor for the receiving thread
	 * @param packet
	 * @param dest
	 * @param theRF
	 * @param abq
	 */
	public Rthread(byte[]packet, short dest, RF theRF, ArrayBlockingQueue abq)
	{
		ablockQ = abq;
	}
	
	
	/**
	 * After an initial line of ouptut, the run() method here just loops forever,
	 * printing periodically as it goes. 
	 */
	public void run() {
		System.out.println("send is alive and well");
		while(true){
			try {
				//transmits what is in the head of the blocking queue
				theRF.transmit(ablockQ.take());

			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				System.out.println(e);
				//e.printStackTrace();
			}
		}

	}

}


