/*
Name: Soumya Das
SB Id#: 110532374

I pledge my honor that all parts of this project were done by me individ-
ually, without collaboration with anyone, and without consulting external
sources that help with similar projects.

*/

package osp.Devices;
import java.util.*;
import osp.IFLModules.*;
import osp.Hardware.*;
import osp.Interrupts.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.FileSys.*;

/*
    The disk interrupt handler.  When a disk I/O interrupt occurs,
    this class is called upon the handle the interrupt.

    @OSPProject Devices
*/
public class DiskInterruptHandler extends IflDiskInterruptHandler
{
    /* 
        Handles disk interrupts. 
        
        This method obtains the interrupt parameters from the 
        interrupt vector. The parameters are IORB that caused the 
        interrupt: (IORB)InterruptVector.getEvent(), 
        and thread that initiated the I/O operation: 
        InterruptVector.getThread().
        The IORB object contains references to the memory page 
        and open file object that participated in the I/O.
        
        The method must unlock the page, set its IORB field to null,
        and decrement the file's IORB count.
        
        The method must set the frame as dirty if it was memory write 
        (but not, if it was a swap-in, check whether the device was 
        SwapDevice)

        As the last thing, all threads that were waiting for this 
        event to finish, must be resumed.

        @OSPProject Devices 
    */
    public void do_handleInterrupt()
    {
        IORB iorb = (IORB)InterruptVector.getEvent();
        OpenFile openFile = iorb.getOpenFile();
        openFile.decrementIORBCount();
        if(openFile.getIORBCount() == 0 && openFile.closePending)
            openFile.close();
        PageTableEntry pageTableEntry = iorb.getPage();
        FrameTableEntry frameTableEntry = pageTableEntry.getFrame();
        pageTableEntry.unlock();
        
        if(iorb.getThread().getTask().getStatus()!=TaskTerm){
            if(iorb.getDeviceID() != SwapDeviceID && iorb.getThread() != ThreadKill ){

                frameTableEntry.setReferenced(true);
                if(iorb.getIOType() == FileRead)
                    frameTableEntry.setDirty(true);
            }
            else
                frameTableEntry.setDirty(false);
        }

        if(iorb.getThread().getTask().getStatus()==TaskTerm && frameTableEntry.isReserved())
            frameTableEntry.setUnreserved(iorb.getThread().getTask());

        iorb.notifyThreads();
        Device device = Device.get(iorb.getDeviceID());
        device.setBusy(false);

        if(device.dequeueIORB() != null)
            device.startIO(device);

        ThreadCB.dispatch();
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */
    }

/*
      Feel free to add local classes to improve the readability of your code
*/
