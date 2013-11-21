package wifi;

import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

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
   private short ourMAC;       // Our MAC address
   private PrintWriter output; // The output stream we'll write to
   //Array Blocking Queue to hold packets being sent
   private ArrayBlockingQueue <byte []> sendBlocQ = new ArrayBlockingQueue(10);
   ///Array Blocking Queue to hold the packets wanted by the next layer
   private ArrayBlockingQueue <byte []> recBlocQ = new ArrayBlockingQueue(10);
   //Array Blocking Queue with unpacked packets to be called from above
   private ArrayBlockingQueue <byte[]> readyBlocQ = new ArrayBlockingQueue(10);
   //to hold the destination address of a packet
   private short destAdd;
   
   public Rthread recThread;
   
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
      //make an instance of the send tread
      Sthread sendthread = new Sthread(theRF, sendBlocQ);
      //start the send thread
      (new Thread(sendthread)).start();
      
      //make an instance of the receiving thread
      recThread = new Rthread(null, ourMAC, theRF, recBlocQ);
      //start the receiving thread
      (new Thread(recThread)).start();
      
      
      
      output.println("LinkLayer: Constructor ran.");
   }

   /**
    * Send method takes a destination, a buffer (array) of data, and the number
    * of bytes to send.  See docs for full description.
    * @return int number of bytes sent
    */
   public int send(short dest, byte[] data, int len) {

	   //only try to build if there is something to send
	   if(dest != 0){
		   output.println("LinkLayer: Sending "+len+" bytes to "+dest);

		   try {
			   sendBlocQ.put(BuildPacket.build(data, dest, ourMAC));
		   } catch (InterruptedException e) {
			   // TODO Auto-generated catch block
			   e.printStackTrace();
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
	   
	   //"block" until data is received
	   while(t.getDestAddr() == 0);
	   output.println("Data received from: " + t.getSourceAddr());
	   output.println("Tx starting from host " + t.getSourceAddr() + " at local time " + theRF.clock());
	   output.println("From " + String.valueOf(t.getSourceAddr()) + ": " + t.getBuf().toString());
	   
	   return BuildPacket.recData.length;
	   //****
	   //writes the incoming data and address information 
	   //into the Transmission instance passed as argument
	   //**

	   //while(true); // <--- This is a REALLY bad way to wait.  Sleep a little each time through.
	   //return 0;
   }

   /**
    * Returns a current status code.  See docs for full description.
    */
   public int status() {
      output.println(theRF.inUse());
      return 0;
   }

   /**
    * Passes command info to your link layer.  See docs for full description.
    */
   public int command(int cmd, int val) {
      output.println("LinkLayer: Sending command "+cmd+" with value "+val);
      return 0;
   }
}
