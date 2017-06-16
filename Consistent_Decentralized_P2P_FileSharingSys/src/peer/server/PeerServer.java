/*=========================================================*/
/*       					         					   */ 
/*	                  Peer As a Server     		           */
/*						       							   */
/*=========================================================*/

package peer.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Vector;

import peer.client.PeerClient;
import peer.client.PeerClientIF;
import peer.client.PeerDirListener;

/**
 * @author Suraj & Lawrence
 * PeerClientServer Implementation
 * task: sends file in peer_client_server to requesting peer
 */
public class PeerServer extends UnicastRemoteObject implements PeerServerIF {
	private PeerClientIF peerClient;
	private String peerIP;
	private String portNo;
	private String peerRootDirectoryPath = null;	//home directory path of peer
	//private String[] files;	//array to hold names of all files and directories in peer's root directory
	//start
	private Vector<FileDoc> fileData = new Vector<FileDoc>();
	private boolean pushprotocol;
	private boolean pullprotocol;
	private int ttr;
	private String[] recentlyAddedFile;	//holds metadata for most recent file
	private int staleFileAction;
	private int total=0;
	private int invalid=0;
	//end	
	private final int QUEUE_MAX_SIZE = 10;
	private String[][] queuedRecMsgs = new String[(int) QUEUE_MAX_SIZE][3];	//keeps track of all received messages's meta data
	private int queuetracker = 0;
	protected PeerServer() throws RemoteException {
		super();
	}
	
	//and finally starts a new thread for event listener 'PeerDirListener(this)'
	public PeerServer(String pIP, String pN) throws RemoteException {
		this.peerIP = pIP;
		this.portNo = pN;
		//create list of files in peer root directory
		try{
			//get peer root directory pathname
			this.peerRootDirectoryPath = System.getProperty("user.dir");
		    System.out.print("Peer Directory is: "+peerRootDirectoryPath.replace("\\", "/")+"\n");
		    //get all files in peer root directory
		    //start
			File f1 = new File(peerRootDirectoryPath+"\\owned");
			String[] ownedfiles = f1.list();	//returns files (with extension) in director
			File f2 = new File(peerRootDirectoryPath+"\\copied");
			String[] copiedfiles = f2.list();	//returns files (with extension) in director
			System.out.println("List of files in Peer:");
			System.out.println("Original Files:");
			String filename, originID = peerIP+":"+portNo;
			long lastmtime;
			for(int i=0; i<ownedfiles.length; i++){
				filename = ownedfiles[i];
				File f = new File(peerRootDirectoryPath+"\\owned\\"+filename);
				lastmtime = f.lastModified();
				FileDoc fileDoc = new FileDoc(this, filename, originID, "owned", "valid", lastmtime, 1, ttr);
				fileData.add(fileDoc);
				System.out.println("  "+filename);
			}
			System.out.println("-----------------------------------");
			System.out.println("Copied files:");
			for(int i=0; i<copiedfiles.length; i++){
				filename = copiedfiles[i];
				File f = new File(peerRootDirectoryPath+"\\copied\\"+filename);
				lastmtime = f.lastModified();
				FileDoc fileDoc = new FileDoc(this, filename, "unknown", "copied", "valid", lastmtime, 1, 0);
				fileData.add(fileDoc);
				System.out.println("  "+filename);
			}
			System.out.println("-----------------------------------");
		}catch (Exception e){
		    System.out.println("Peer path Exception caught ="+e.getMessage());
		}		
		//start thread of event listener to monitor changes in both peer sub-directories
		new Thread(new PeerDirListener(this, "owned")).start();	
		new Thread(new PeerDirListener(this, "copied")).start();
		//end
	}
	
	public String getIP(){
		return peerIP;
	}
	public String getPN(){
		return portNo;
	}
	public String getPeerDir() {
		return peerRootDirectoryPath;
	}
	public void setClientInstance(PeerClientIF client){
		peerClient = client;
	}
	//start
	public Vector<FileDoc> getFileData() throws RemoteException {
		return fileData;
	}
	public void setFileData(Vector<FileDoc> fileData) {
		this.fileData = fileData;
	}
	public boolean getPushprotocol() {
		return pushprotocol;
	}
	public void setPushprotocol(boolean pushprotocol) throws RemoteException {
		this.pushprotocol = pushprotocol;
	}
	public boolean getPullprotocol() {
		return pullprotocol;
	}
	public void setPullprotocol(boolean pullprotocol) throws RemoteException {
		this.pullprotocol = pullprotocol;
	}
	public void setRecentlyAddedFile(String fn, String oID, String st, int vnum) throws RemoteException {
		String[] metadata = {fn, oID, st, Integer.toString(vnum)};
		this.recentlyAddedFile = metadata;
	}
	public int getStaleFileAction() throws RemoteException {
		return staleFileAction;
	}
	public void setStaleFileAction(int staleFileAction) throws RemoteException {
		this.staleFileAction = staleFileAction;
	}
	public int getTTR() throws RemoteException {
		return ttr;
	}
	public void setTTR(int ttr) throws RemoteException {
		this.ttr = ttr;
	}
	public synchronized void updateFileListCtr(String dirType) throws RemoteException {
		System.out.println("--------A file has been created/added to folder: "+dirType);
		//get all files in peer root directory
		File f = new File(peerRootDirectoryPath+"\\"+dirType);
		String[] files = f.list();	//returns directory and files (with extension) in directory
		String fname;
		FileDoc fdoc;
		for (int i=0; i<files.length; i++){
			boolean notfound = true;
			fname = files[i];
			//System.out.println(fname);
			for (int j=0; j<fileData.size(); j++){
				fdoc = fileData.get(j);
				if (fdoc.getFolderType().equals(dirType) && fdoc.getFilename().equals(fname))
					notfound = false;
			}
			if (notfound){
				//System.out.println("file found: "+fname);
				f = new File(peerRootDirectoryPath+"\\"+dirType+"\\"+fname);
				long lastmtime = f.lastModified();
				if (dirType.equals("owned")){
					String originID = peerIP+":"+portNo;
					FileDoc fileDoc = new FileDoc(this, fname, originID, dirType, "valid", lastmtime, 1, ttr);
					fileData.add(fileDoc);
				} else if (dirType.equals("copied")){
					String fn=recentlyAddedFile[0];
					String oid=recentlyAddedFile[1];
					String st=recentlyAddedFile[2];
					int fvn=Integer.parseInt(recentlyAddedFile[3]);
					//System.out.println("file metadata: "+fn+" "+oid+" "+st+" "+fvn);
					if (fname.equals(fn)){
						FileDoc fileDoc = new FileDoc(this, fname, oid, dirType, st, lastmtime, fvn, ttr);
						fileData.add(fileDoc);
						//System.out.println("file added to list");
					} else {
						FileDoc fileDoc = new FileDoc(this, fname, "unknown", dirType, "valid", lastmtime, 1, ttr);
						fileData.add(fileDoc);
						//System.out.println("brand new file added to list");
					}
				}
			}
		}
	}
	public synchronized void updateFileListDel(String dirType) throws RemoteException {
		System.out.println("--------a file has been deleted from folder: "+dirType);
		//get all files in peer root directory
		File f = new File(peerRootDirectoryPath+"\\"+dirType);
		String[] files = f.list();	//returns directory and files (with extension) in directory
				
		String fname = null;
		FileDoc fdoc;
		for (int j=0; j<fileData.size(); j++){
			fdoc = fileData.get(j);
			if (fdoc.getFolderType().equals(dirType)){
				boolean notfound = true;
				fname = fdoc.getFilename();
				for (int i=0; i<files.length; i++){
					if (fname.equals(files[i])){
						notfound = false;
					}
				}
				if (notfound){
					fileData.remove(j);
					System.out.println("file: "+fname+" removed from list");
				}
			}
		}
	}
	public synchronized void updateFileListMod(String dirType) throws RemoteException {
		System.out.println("--------a file has been modified in folder: "+dirType);
		//get all files in peer root directory
		File f = new File(peerRootDirectoryPath+"\\"+dirType);
		String[] files = f.list();	//returns directory and files (with extension) in directory
		String fname;
		long modtime;
		FileDoc fdoc;
		int vn;
		//find modified file		
		for (int i=0; i<files.length; i++){
			fname = files[i];
			f = new File(peerRootDirectoryPath+"\\"+dirType+"\\"+fname);
			modtime = f.lastModified();
			//System.out.println(fname+" last modtime: "+modtime);
			for (int j=0; j<fileData.size(); j++){
				fdoc = fileData.get(j);
				if (fdoc.getFolderType().equals(dirType) && fdoc.getFilename().equals(fname)){
					if (fdoc.getLastModified() != modtime){
						//System.out.println(fname+" new last modtime: "+fdoc.getLastModified());
						System.out.println("modified file found");
						//update last modified time and increment version number
						fdoc.setLastModified(modtime);
						vn = fdoc.getVersionNum()+1;
						fdoc.setVersionNum(vn);
						//broadcast if push-based and make sure only owned files can trigger validation
						if(pushprotocol && dirType.equals("owned")){
							System.out.println("trigger push protocol");
							String msgID = peerIP+"-"+portNo+"-"+peerClient.getMsgIDsuffix();
							String originID = fdoc.getOriginServerID();
							invalidate(msgID, peerIP, portNo, originID, fname, vn, peerClient.getName());
						}
					}
				}
			}
		}
	}
	public synchronized void invalidate(String msgID, String cIP, String cPN, String originID, String fname, int vn, String pname) throws RemoteException {
		System.out.println("push invalidation testing...");
		//invalidate file if present
		boolean originPeer = (peerIP.equals(cIP) && portNo.equals(cPN));	//test to know if in origin peer
		//System.out.println(originPeer);
		if(!originPeer){
			System.out.println("checkpoint 1");
			FileDoc foc;
			for (int j=0; j<fileData.size(); j++){
				foc = fileData.get(j);
				if (foc.getFolderType().equals("copied") && foc.getFilename().equals(fname)){
					System.out.println("checkpoint 2");
					if (foc.getVersionNum() != vn){
						System.out.println("Message Interrupt: File Invalidation; File '"+fname+"' is out of date");
						new Thread(new handleInvalidation(foc, fname, peerClient, this)).start();
					} else {
						System.out.println("Message Interrupt: File Invalidation; File '"+fname+"' is still up to date");
					}
				}
			}
		}	
		//broadcast message to other neighboring peers
		String[][] neighbors =  peerClient.getNeighPeerServers();
		for (int l=0; l<neighbors.length; l++) {
			if(!(neighbors[l][0].equals(cIP) && neighbors[l][1].equals(cPN))) {
				PeerServerIF neighPeerServer;
				try {
					neighPeerServer = (PeerServerIF) Naming.lookup("rmi://"+neighbors[l][0]+":"+neighbors[l][1]+"/peerserver");
					neighPeerServer.invalidate(msgID, peerIP, portNo, originID, fname, vn, pname);
				} catch (MalformedURLException | NotBoundException e) {
					System.out.println("MalformedURLException | NotBoundException error");
					e.printStackTrace();
				}
			}
		}	
	}
	public ArrayList<String> getFileList() throws RemoteException {
		ArrayList<String> files = new ArrayList<String>();
		for (int i=0; i<fileData.size(); i++){
			files.add(fileData.get(i).getFilename());
		}
		return files;
	}
	public int pullValidation(String fname, int versionNum) {
		FileDoc docfile;
		for (int h=0; h<fileData.size(); h++){
			docfile = fileData.get(h);
			if (docfile.getFolderType().equals("owned") && docfile.getFilename().equals(fname)){	//file must be a 'owned' document
				if (docfile.getVersionNum() == versionNum)
					return ttr;
				else
					return -1;
			}
		}
		return -2;
	}
	//end
	
	/**
		 * task: sends a requested file to the requesting peer by converting
		 * 		the file to message stream using 'FileInputStream' and calling
		 * 		'c.acceptFile(f1.getName(), mydata, mylen)'
		 * @param c: requesting peer object
		 * @param file: filename of the file to be sent from peer server to requesting peer 
		 * @return returns true if file is sent successfully
		 * @throws RemoteException
	 */
	public synchronized boolean sendFile(PeerClientIF c, String file) throws RemoteException{
		//get file metadata
		FileDoc doc;
		String masterID = null, state = null, folder = null;
		int vn = 0;
		for (int i=0; i<fileData.size(); i++){
			doc = fileData.get(i);
			if(doc.getFilename().equals(file)){
				masterID = doc.getOriginServerID();
				state = doc.getConsistency();
				folder = doc.getFolderType();
				vn = doc.getVersionNum();
			}
		}
		//Sending The File...
		try{
			File f1 = new File(folder+"\\"+file);	//copy file to be sent	
			//convert to message stream	
			FileInputStream in = new FileInputStream(f1);		 				 
			byte[] mydata = new byte[1024*1024];						
			int mylen = in.read(mydata);
			while(mylen>0){
				if(c.acceptFile(f1.getName(), mydata, mylen, masterID, state, vn)){
					System.out.println("   File '"+file+"' has been sent to Requesting Peer: "+c.getName());
					mylen = in.read(mydata);
				} else {
					System.out.println("Fault: File was NOT sent");
				}				 
			}
		 }catch(Exception e){
			 e.printStackTrace(); 
		 }
		 return true;
	}
	public void update(String filename)
	{
		File f = new File(peerRootDirectoryPath+"\\owned");
		String filepath = "owned\\"+filename;
		System.out.println(filepath);
	    FileWriter fw;
		try {
			fw = new FileWriter(filepath,true);
			fw.write("\n hello \n");//appends the string to the file
		    fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} //the true will append the new data
	    
	}
	/**
	 * task: spuns two threads. one to search itself for the file and the other to
	 * 		subsequently query all neighboring peers
	 * @param file: filename of the file to be found
	 * @param requestingPeer: name of the peer seeking to find the file
	 * @param flag: controls number of time message is printed
	 * @return returns the compiled lists (array) of peers that contains file if
	 * 		file is found. Else it returns 'null' signifying that the file was not 
	 * 		found and hence is not present in any peer
	 * @throws RemoteException
	 */
	public synchronized void query(String msgID, String cIP, String cPN, long timeToLive, String file, String cPName) throws RemoteException {
		System.out.println("   Peer '"+cPName+"' has requested a file: "+file);
		boolean fileNotFound = true;
		boolean originmessage = false;
		//record received message in logical priority queue
		//override stale/oldest elements if logical queue is full
		queuetracker = queuetracker%QUEUE_MAX_SIZE;
		queuedRecMsgs[queuetracker][0] = msgID;
		queuedRecMsgs[queuetracker][1] = cIP;
		queuedRecMsgs[queuetracker][2] = cPN;
		queuetracker++;
		
		if(getFileList().contains(file)) {
			fileNotFound = false;
			int index = 0;
			System.out.println("   File found in "+peerClient.getName());
			PeerServerIF msgSender;
			try {
				//assembling unique url for each neighboring peer and searching for that unique url in register
				msgSender = (PeerServerIF) Naming.lookup("rmi://"+cIP+":"+cPN+"/peerserver");
				for(int k=0;k<fileData.size();k++)
				{
					if(fileData.get(k).getFilename().equals(file)) 
						index = k;
				}
				if(fileData.get(index).getOriginServerID().equals(peerIP+":"+portNo))	
					originmessage = true;
				//total=0;
				//for(int i=0;i<200;i++)
				msgSender.queryhit(msgID, timeToLive+1, file, peerIP, portNo, peerClient.getName(),fileData.get(index).getLastModified(),originmessage);
			} catch (MalformedURLException | NotBoundException e) {
				System.out.println("MalformedURLException or NotBoundException in PeerServer's query method");
				e.printStackTrace();
			}
		}
		//broadcast message to other neighboring peers
		String[][] neighbors =  peerClient.getNeighPeerServers();
		if(timeToLive > 0) {
			for (int l=0; l<neighbors.length; l++) {
				if(!(neighbors[l][0].equals(cIP) && neighbors[l][1].equals(cPN))) {
					PeerServerIF neighPeerServer;
					try {
						neighPeerServer = (PeerServerIF) Naming.lookup("rmi://"+neighbors[l][0]+":"+neighbors[l][1]+"/peerserver");
						neighPeerServer.query(msgID, peerIP, portNo, timeToLive-1, file, peerClient.getName());
					} catch (MalformedURLException | NotBoundException e) {
						System.out.println("MalformedURLException | NotBoundException error");
						e.printStackTrace();
					}
				}
			}
		}	
		if(fileNotFound)
			System.out.println("   File not found in "+peerClient.getName());
	}
	public void queryhit(String msgID, long timeToLive, String filename, String hitPeerIP, String hitPeerPN, String hitPeerName, long lastmodified, boolean originserver) throws RemoteException {
		//int total = 0, invalid = 0;
		if(timeToLive == PeerClient.getTTL()){
			peerClient.addMsgHits(msgID, hitPeerIP, hitPeerPN, hitPeerName);
			total++;
			int index = 0;
			/*if(originserver)
			{*/
				PeerServerIF msgInvalid;
				try {
					msgInvalid = (PeerServerIF) Naming.lookup("rmi://"+hitPeerIP+":"+hitPeerPN+"/peerserver");
					for(int k=0;k<fileData.size();k++)
					{
						if(msgInvalid.getFileData().get(k).getFilename().equals(filename)) 
							index = k;
					}
					if(lastmodified < msgInvalid.getFileData().get(index).getLastModified()) invalid++;
				} catch (MalformedURLException | NotBoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			System.out.println("percentage of invalid query results" + (invalid/total));
		} else {
			for(int r=0; r<queuedRecMsgs.length; r++) {
				if (queuedRecMsgs[r][0] != null) {
					if(queuedRecMsgs[r][0].equals(msgID)) {
						PeerServerIF msgUpStreamSender;
						try {
							msgUpStreamSender = (PeerServerIF) Naming.lookup("rmi://"+queuedRecMsgs[r][1]+":"+queuedRecMsgs[r][2]+"/peerserver");
							msgUpStreamSender.queryhit(msgID, timeToLive+1, filename, hitPeerIP, hitPeerPN, hitPeerName,lastmodified, originserver);
						} catch (MalformedURLException | RemoteException | NotBoundException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
}
//start
class handleInvalidation implements Runnable 
{
	private FileDoc foc;
	private String fname;
	private PeerClientIF pc;
	private PeerServerIF ps;
	public handleInvalidation(FileDoc foc, String fname, PeerClientIF pc, PeerServerIF ps){
		this.foc = foc;
		this.fname = fname;
		this.pc = pc;
		this.ps = ps;
	}
	
	public void run()
	{
		foc.setConsistency("invalid");
		int option;
		try {
			option = ps.getStaleFileAction();
			if (option != 3) {
				File d = new File(ps.getPeerDir()+"\\"+foc.getFolderType()+"\\"+fname);
				d.delete();
				if (option == 1){
					System.out.println("deleting file...");
					System.out.println("File '"+fname+"' is deleted");
				} else if (option == 2){
					String fosid = foc.getOriginServerID();
					System.out.println("re-downloading file...");
					System.out.println("file's origin server id: "+fosid);
					if (fosid.equals("unknown")){
						System.out.println("Warning: File will not be downloaded because orign server ID is unknown\n"
								+ "This implieas that the file was not pre-downloaded during this session. You may only"
								+ "re-download files that were pre-downloaded during current session");
					} else {
						//connect peer directly to origin peer server through RMI in order to download file
						String url = "rmi://"+fosid+"/peerserver";
						PeerServerIF originServer = (PeerServerIF) Naming.lookup(url);
						pc.downloadFile(originServer, fname);	//download file from chosen peer
					}
				}
			}
		} catch (RemoteException | MalformedURLException | NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
//end