package edu.cuny.brooklyn.cisc3120.project.game.net;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

public class StatusBroadcaster {
	private final static Logger LOGGER = LoggerFactory.getLogger(StatusBroadcaster.class);

	private final static String TIMER_NAME = "status_broadcaster";
	private final static long LONG_DELAY_BEFORE_1ST_RUN = 5000; // 5000 milliseconds
	private final static long PERIOD_BETWEEN_RUNS = 10000; // 10000 milliseconds
	private final static int BROADCAST_UDP_PORT = 62017;
	private final static int BUFFER_SIZE = 8096;
	private Timer timer;
	private TimerTask task;
	private DatagramSocket socket;
	private byte[] buf;
	private DatagramPacket packet;
	private VBox playersList;
	private int tcpPort;

	public StatusBroadcaster(VBox playersList) throws IOException {
		this.playersList = playersList;
		timer = new Timer(TIMER_NAME);
		socket = new DatagramSocket();
		socket.setBroadcast(true);
		if (!socket.getBroadcast()) {
			socket.close();
			throw new SocketException("Broadcast is not supported.");
		}
		buf = new byte[BUFFER_SIZE];
		packet = new DatagramPacket(buf, buf.length);

		/**
		 * this is for supporting the bonus requirement: multiple players play the game online
		 */
		tcpPort = getFreeTcpPort();
	}

	public synchronized void close() {
		socket.close();
		socket = null;
		timer.cancel();
		LOGGER.debug("closing the UDP broadcaster.");
	}

	public void start() throws IOException, InterruptedException {
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

			while (interfaces.hasMoreElements()) {
				for (InterfaceAddress networkInterface : interfaces.nextElement().getInterfaceAddresses()) {
					Thread thread = new Thread(() -> {
						DatagramSocket socket = null;
						try {
							socket = new DatagramSocket(BROADCAST_UDP_PORT, networkInterface.getAddress());
							socket.setBroadcast(true);
						}catch(IOException e){
							e.printStackTrace();
						}

						while (true) {
							byte[] recvBuf = new byte[15000];
							DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
							try {
								if (socket != null) {
									socket.receive(packet);

									String message = packet.getAddress().toString() + ":" + packet.getPort();

									LOGGER.debug(message);

									boolean found = false;
									for (Object child: ((ListView)playersList.getChildren().get(1)).getItems()) {
										if(((TextField)child).getText().equals(message)) {
											found = true;
										}
									}

									if(!found) {
										TextField child = new TextField();

										child.setText(message);
										((ListView)playersList.getChildren().get(1)).getItems().add(child);
									}
								} else {
									break;
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});

					thread.start();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		task = new TimerTask() {
			@Override
			public void run() {
				Enumeration<NetworkInterface> interfaces;
				try {
					interfaces = NetworkInterface.getNetworkInterfaces();


					while (interfaces.hasMoreElements()) {
						NetworkInterface networkInterface = interfaces.nextElement();
						for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
							InetAddress broadcast = interfaceAddress.getBroadcast();
							if (broadcast == null)
								continue;

							StatusMessage message = new StatusMessage(interfaceAddress.getAddress(), tcpPort);

							try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
							     ObjectOutputStream oos = new ObjectOutputStream(baos)) {
								oos.writeObject(message);
								oos.flush();

								byte[] sendBuf = baos.toByteArray();
								packet = new DatagramPacket(sendBuf, sendBuf.length, broadcast, BROADCAST_UDP_PORT);
								synchronized (this) {
									if (socket != null && !socket.isClosed()) {
										socket.send(packet);
										LOGGER.debug("send packet: " + message.toString());
									}
								}
							}
						}
					}
				} catch (SocketException e) {
					LOGGER.error("Socket error.", e);
				} catch (IOException e) {
					LOGGER.error("I/O exception.", e);
				}
			}

			;
		};
		timer.schedule(task, LONG_DELAY_BEFORE_1ST_RUN, PERIOD_BETWEEN_RUNS);
	}

	private int getFreeTcpPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			socket.setReuseAddress(true);
			return socket.getLocalPort();
		}
	}
}
