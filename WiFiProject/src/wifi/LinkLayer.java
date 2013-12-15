package wifi;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import rf.RF;
//import src.wifi.BuildPacket;
//import src.wifi.Sthread;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 */
public class LinkLayer implements Dot11Interface {
	private RF theRF; // You'll need one of these eventually
	static short ourMAC;       // Our MAC address
	static PrintWriter output; // The output stream we'll write to
	//Array Blocking Queue to hold packets being sent
	private ArrayBlockingQueue <byte []> sendBlocQ = new ArrayBlockingQueue(5);
	///Array Blocking Queue to hold the packets received from various sources
	private ArrayBlockingQueue <byte []> recBlocQ = new ArrayBlockingQueue(10);
	//Array Blocking Queue with unpacked packets to be called from above
	private ArrayBlockingQueue <byte[]> readyBlocQ = new ArrayBlockingQueue(10);
	//to hold the destination address of a packet
	private short destAdd; 

	//our threads that will send and receive
	public Rthread recThread;
	public Sthread sendThread;

	//for setting the diagnostic level
	static AtomicBoolean diagOn = new AtomicBoolean(false);
	//determines if the slot selection window is fixed or random
	static AtomicBoolean fixedWin = new AtomicBoolean(false);
	//used to set delay between BEACON transmissions
	static long setBeacFreq;
	//used for Status
	static AtomicInteger setStatus = new AtomicInteger(1);

	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 * @param ourMAC  MAC address
	 * @param output  Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		this.ourMAC = ourMAC;
		this.output = output;      
		theRF = new RF(null, null);
		//make an instance of the send tread with array of packets to be sent
		sendThread = new Sthread(theRF, sendBlocQ);
		//start the send thread
		(new Thread(sendThread)).start();

		//make an instance of the receiving thread with array of received packets?
		recThread = new Rthread(null, ourMAC, theRF, recBlocQ);
		//start the receiving thread
		(new Thread(recThread)).start();

		//set a starting delay between BEACON transmissions
		setBeacFreq = 30000000;//30 seconds

		output.println("LinkLayer: Constructor ran.");
		//update status
		setStatus.set(1);//SUCCESS
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 * @return int number of bytes sent
	 */
	public int send(short dest, byte[] data, int len) {
		if(dest != 0){
			output.println("LinkLayer: Sending "+len+" bytes to "+dest);
			//keep sending

			//only add to the Q if it isn't full
			if(sendBlocQ.size() < 5){

				try {
					sendBlocQ.put(BuildPacket.build(data, dest, ourMAC, (short) 0));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else{
				//Queue is full!!!
				//set status
				setStatus.set(10);//INSUFFICIENT_BUFFEER_SPACE
				diagOut("Couldn't send because the queue is already full.");
				return 0;
			}
			
		}

		return len;

	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 * @return int number of bytes recieved, -1 on error
	 */
	public int recv(Transmission t) {
		output.println("LinkLayer: Pretending to block on recv()");
		//data is in A.B.Q.

		output.println("Recieve in LinkLAYER????");


		//output.println(recBlocQ);
		t.setBuf(recThread.getData());
		t.setDestAddr(recThread.getDestAdd());
		t.setSourceAddr(recThread.getSrcAdd());

		//sleep for 5 seconds
		//try {
		/* Thread.sleep(500);
	   } catch (InterruptedException e) {
		   // TODO Auto-generated catch block
		   e.printStackTrace();
	   }*/
		System.out.println("data can be called from above");
		String thetext= null;
		//"block" until data is received
		while(t.getDestAddr() == 0);
		try {
			thetext = new String(t.getBuf(),"US-ASCII");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		output.println("Data received from: " + t.getSourceAddr());
		output.println("Tx starting from host " + t.getSourceAddr() + " at local time " + theRF.clock()+Rthread.fudge.get());
		output.println("From " + String.valueOf(t.getSourceAddr()) + ": " + thetext);

		return recThread.getData().length;
		//****
		//writes the incoming data and address information 
		//into the Transmission instance passed as argument
		//**

		//while(true); // <--- This is a REALLY bad way to wait.  Sleep a little each time through.
		//return 0;
	}

	/**
	 * Sets a status code upon the completion of each interface routine. 
	 * Also sets the status code for each attempted packet transmission. 
	 * Returns the current status of the 802.11~ layer.  See docs for full description.
	 * 
	 * SUCCESS if all went well, or the appropriate error code if not
	 * TX_DELIVERED if the packet was acknowledged
	 * TX_FAILED otherwise
	 * 
	 * @return Status code
	 */
	public int status() {
		//output.println(theRF.inUse());
		if(setStatus.get() == 1){
			output.println("SUCCESS");
		}
		if(setStatus.get() == 2){
			output.println("UNSPECIFIED_ERROR");
		}
		if(setStatus.get() == 3){
			output.println("RF_INIT_FAILED");
		}
		if(setStatus.get() == 4){
			output.println("TX_DELIVERED");
		}
		if(setStatus.get() == 5){
			output.println("TX_FAILED");
		}
		if(setStatus.get() == 6){
			output.println("BAD_BUF_SIZE");
		}
		if(setStatus.get() == 7){
			output.println("BAD_ADDRESS");
		}
		if(setStatus.get() == 8){
			output.println("BAD_MAC_ADDRESS");
		}
		if(setStatus.get() == 9){
			output.println("ILLEGAL_ARGUMENT");
		}
		if(setStatus.get() == 10){
			output.println("INSUFFICIENT_BUFFER_SPACE");
		}
		return setStatus.get();
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 * 
	 * @param cmd is numbers 0,1,2,3 indicating a desired command
	 * 		0 - Print current settings
	 * 		1 - Set the diagnostic level
	 * 		2 - Select whether slot detection is random or fixed
	 * 		3 - Set the delay, in seconds, between transmission of Beacon frames
	 * @param val is the value associated with the command
	 * @return response from the command
	 */
	public int command(int cmd, int val) {
		output.println("LinkLayer: Sending command "+cmd+" with value "+val);

		if(cmd == 0){
			//Print current settings*****FINISH*****
			output.println("Command 0 Options and Settings - Values --> Ignored");
			output.println("Command 1 Diagnostic Level - Values --> 0: Off , 1: On");
			output.println("Command 2 Slot Selection - Values --> 0: Random, 1: Fixed at Max");
			output.println("Command 3 Beacon Interval - Values --> int: Delay in Seconds");
			output.println("Current Settings: ");
			if(diagOn.get() == false){
				output.println("Diagnostic Level: " + "Off");
			}else{
				output.println("Diagnostic Level: " + "On");
			}
			if(fixedWin.get() == false){
				output.println("Slot Selection: " + "Random");
			}else{
				output.println("Slot Selection: " + "Fixed");
			}
			output.println("Beacon Interval: " + (setBeacFreq/1000000));
		}

		if (cmd == 1){
			//Set the diagnostic level
			//turn diagnostic output on/off
			if (val == 0){
				diagOn.set(false);
				output.println("Diagnostic Level is set to: OFF");  
			}
			if (val ==1){
				diagOn.set(true);
				output.println("Diagnostic Level is set to: ON ");
			}

		}

		if (cmd == 2){
			//Select whether slot selection is random or fixed
			if (val == 0){
				//random slot selection window
				fixedWin.set(false); 
				output.println("Slot Detection: Random");
			}
			if(val == 1){
				//fixed slot selection window
				fixedWin.set(true);
				output.println("Slot Detection: Fixed");
			}
		}

		if (cmd == 3){
			//Set the delay, in seconds, between transmission of Beacon frames
			setBeacFreq = (long)(val*1000000);
			output.println("Beacon Transmission Delay: " + val + " seconds");
		}

		if(cmd>3){
			output.println("The command" + cmd + " is not supported by the Link Layer");
		}

		return 0;
	}

	public static void diagOut(String out){
		if(LinkLayer.diagOn.get() == true){
			LinkLayer.output.println(out);
		}
	}
}
