/*=========================================================*/
/*       					         					   */ 
/*	            Peer As Client Interface				   */
/*						       							   */
/*=========================================================*/

package peer.client;

import java.rmi.Remote;
import java.rmi.RemoteException;

import peer.server.PeerServerIF;

/**
 * @author Lawrence
 * PeerClientIF Implementation
 */
public interface PeerClientIF extends Remote {
	String getName() throws RemoteException;
	String getport_no() throws RemoteException;
	String getpeer_ip() throws RemoteException;
	String[][] getNeighPeerServers() throws RemoteException;
	void addMsgHits(String msgID, String hitPeerIP, String hitPeerPN, String hitPeerName) throws RemoteException;
	
	//start
	int getMsgIDsuffix() throws RemoteException;
	void downloadFile(PeerServerIF peerWithFile, String filename) throws RemoteException;
	boolean acceptFile(String filename, byte[] data, int len, String originPeerID, String fileState, int versionNum) throws RemoteException;
	//end
}
