/*
Name: Soumya Das
SB Id#: 110532374

I pledge my honor that all parts of this project were done by me individ-
ually, without collaboration with anyone, and without consulting external
sources that help with similar projects.

*/

package osp.Devices;

/*
    This class stores all pertinent information about a device in
    the device table.  This class should be sub-classed by all
    device classes, such as the Disk class.

    @OSPProject Devices
*/

import osp.IFLModules.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Hardware.*;
import osp.Memory.*;
import osp.FileSys.*;
import osp.Tasks.*;
import java.util.*;

public class Device extends IflDevice
{
    /*
        This constructor initializes a device with the provided parameters.
	As a first statement it must have the following:

	    super(id,numberOfBlocks);

	@param numberOfBlocks -- number of blocks on device

        @OSPProject Devices
    */

      private GenericQueueInterface insertQueue =  null;
      private GenericQueueInterface removeQueue = null;
      private boolean isFirstElement = true; //This would be for the first time the do_dequeue() is run
      private static int prevCylinder = 0;


    public Device(int id, int numberOfBlocks)
    {
        super(id, numberOfBlocks);
        iorbQueue = new GenericList();

        //Create the first queue append it to the list of queue
        GenericQueueInterface queue = new GenericList();
        (GenericList)iorbQueue.append(queue);
        insertQueue = queue;
        removeQueue = queue;

    }

    /*
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Devices
    */
    public static void init()
    {
        // your code goes here

    }

    /*
       Enqueues the IORB to the IORB queue for this device
       according to some kind of scheduling algorithm.
       
       This method must lock the page (which may trigger a page fault),
       check the device's state and call startIO() if the 
       device is idle, otherwise append the IORB to the IORB queue.

       @return SUCCESS or FAILURE.
       FAILURE is returned if the IORB wasn't enqueued 
       (for instance, locking the page fails or thread is killed).
       SUCCESS is returned if the IORB is fine and either the page was 
       valid and device started on the IORB immediately or the IORB
       was successfully enqueued (possibly after causing pagefault pagefault)
       
       @OSPProject Devices
    */
    public int do_enqueueIORB(IORB iorb)
    {     
          //lock the page associated with the iorb
          iorb.getPage().lock();

          //increment iorb count
          iorb.getOpenFile().incrementIORBCount();

          //Compute Cylinder
          int addBits = MMU.getVirtualAddressBits();
          int pageBits = MMU.getPageAddressBits();
          int offsetBits = addBits - pageBits;
          int blockSize = (int)Math.pow(2,offsetBits); //Disk block size is equal to page size
          int sectorsPerBlock = blockSize/this.getBytesPerSector();
          int blocksPerTrack = this.getSectorsPerTrack()/sectorsPerBlock;
          int tracksPerCylinder = this.getPlatters();
          int blockNumber = iorb.getBlockNumber();
          int cylinder = blockNumber/(blocksPerTrack*tracksPerCylinder);

          iorb.setCylinder(cylinder);

          int threadStatus = iorb.getThread().getStatus();

          if(threadStatus == ThreadKill)
               return FAILURE;

          //Thread is alive
          //Device is idle
          if(!this.isBusy())
               this.startIO(iorb);
          //Device is busy
          else
               (GenericList)insertQueue.append(iorb);

           return SUCCESS;

    }

    /*
       Selects an IORB (according to some scheduling strategy)
       and dequeues it from the IORB queue.

       @OSPProject Devices
    */
    public IORB do_dequeueIORB()
    {
        
        IORB iorb = SSTF();

        //Dequeuing for the first time
        if(isFirstElement == true && iorb != null){

          isFirstElement = false;
          //Create a new queue append it to the list of queue
          GenericQueueInterface queue = new GenericList();
          (GenericList)iorbQueue.append(queue);
          insertQueue = queue;   
        }

        //Dequeuing the element left in a list
        if(iorb != null && removeQueue.isEmpty()){

          iorbQueue.removeHead();
          removeQueue = iorbQueue.getHead();
          //Create a new queue append it to the list of queue
          GenericQueueInterface queue = new GenericList();
          (GenericList)iorbQueue.append(queue);
          insertQueue = queue;   
        }

        return iorb;
          
    }

    private IORB SSTF(){

     if(removeQueue.isEmpty())
          return null;

     IORB minIorb = (IORB)((GenericList)removeQueue).getHead();
     Enumeration list = removeQueue.forwardIterator();
     while(list.hasMoreElements()){

          IORB iorb = (IORB)list.nextElement();
          if(Math.abs(iorb.getCylinder()- prevCylinder) < Math.abs(minIorb.getCylinder()-prevCylinder))
               minIorb = iorb;    
     }

     prevCylinder = minIorb.getCylinder();
     return (IORB)removeQueue.remove(minIorb);

    }

    /*
        Remove all IORBs that belong to the given ThreadCB from 
	this device's IORB queue

        The method is called when the thread dies and the I/O 
        operations it requested are no longer necessary. The memory 
        page used by the IORB must be unlocked and the IORB count for 
	the IORB's file must be decremented.

	@param thread thread whose I/O is being canceled

        @OSPProject Devices
    */
    public void do_cancelPendingIO(ThreadCB thread)
    {
        if(thread == null)
          return;

        Enumeration list = insertQueue.forwardIterator();
        while(list.hasMoreElements()){

          IORB iorb = (IORB)list.nextElement();
          if(iorb.getThread().equals(thread)){

               iorb.getPage().unlock();
               OpenFile openFile = iorb.getOpenFile();
               openFile.decrementIORBCount();
               if(openFile.getIORBCount() == 0 && openFile.closePending)
                    openFile.close();
          }
     }

     list = removeQueue.forwardIterator();
        while(list.hasMoreElements()){

          IORB iorb = (IORB)list.nextElement();
          if(iorb.getThread().equals(thread)){

               iorb.getPage().unlock();
               OpenFile openFile = iorb.getOpenFile();
               openFile.decrementIORBCount();
               if(openFile.getIORBCount() == 0 && openFile.closePending == true)
                    openFile.close();
          }
     }

    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
	
	@OSPProject Devices
     */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
