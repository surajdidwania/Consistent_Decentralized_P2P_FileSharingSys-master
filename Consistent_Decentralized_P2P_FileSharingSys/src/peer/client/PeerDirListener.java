/*=========================================================*/
/*       					         					   */ 
/*	             Peer Event Listener			           */
/*						       							   */
/*=========================================================*/

package peer.client;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import peer.server.PeerServer;
import peer.server.PeerServerIF;


/**
 * @author Lawrence
 * PeerDirListener Implementation
 * task: implements event listener that monitors the root directory of a peer
 * 		and automatically updates the central server as soon as a change is 
 * 		made to the contents of the peer's root directory.
 * 		It listens specifically for file creation, deletion, and modification
 */
public class PeerDirListener implements Runnable {
	private PeerServerIF peerServer;	//object of client
	private String dirType;
	public PeerDirListener(PeerServer peerServer, String folder) {
		this.peerServer = peerServer;
		this.dirType = folder;
	}

	@Override
	public void run() {
		try {
			System.out.println(dirType+" Directory listener has started");
			//creating an event listener to monitor peer directory
			//public void peerDirListener() throws IOException {
			WatchService watcher = FileSystems.getDefault().newWatchService();
			Path dir = Paths.get(peerServer.getPeerDir()+"\\"+dirType);
		    WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, 
		    			StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
		    //infinite loop to listen for changes and immediately update file list
		    boolean doUpdateForNewFile = true;
		    for (;;) {
		    	// wait for key to be signaled
		        try {
		        	key = watcher.take();
		        } catch (InterruptedException x) {
		            return;
		        }
		        //boolean doUpdateForNewFile = true;
		        for (WatchEvent<?> event: key.pollEvents()) {
		            WatchEvent.Kind<?> kind = event.kind();				            
		            // This key is registered only for ENTRY_CREATE, ENTRY_DELETE, and ENTRY_MODIFY events,
		            // but an OVERFLOW event can occur regardless if events are lost or discarded.
		            if (kind == StandardWatchEventKinds.OVERFLOW) {
		                continue;
		            }
		            if (kind==StandardWatchEventKinds.ENTRY_DELETE){
		            	//System.out.println("--------directory listener: deleted");
		            	key.reset();
		            	peerServer.updateFileListDel(dirType);
		            }
		            if (kind==StandardWatchEventKinds.ENTRY_MODIFY){
		            	if(doUpdateForNewFile){		//because when a new file is created both ENTRY_MODIFY and ENTRY_CREATE are called
		            		//System.out.println("--------directory listener: modified");
		            		key.reset();
		            		peerServer.updateFileListMod(dirType);
		            	} else
		            		doUpdateForNewFile = true;
		            }
		            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
		            	//System.out.println("--------directory listener: created");
		               	key.reset();
		            	peerServer.updateFileListCtr(dirType);
		            	doUpdateForNewFile = false;
		            }
		        }
		        // Reset the key -- this step is critical if you want to
		        // receive further watch events.  If the key is no longer valid,
		        // the directory is inaccessible so exit the loop.
		        boolean valid = key.reset();
		        if (!valid) {
		            break;
		        }
		    }
		} catch (IOException x) {
		    System.err.println(x);
		}
	}	
}