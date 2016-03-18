import java.io.BufferedReader;
import java.io.InputStreamReader;
//import java.io.OutputStream;

import java.util.Date;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

import gnu.io.CommPortIdentifier; 
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent; 
import gnu.io.SerialPortEventListener;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class CMHelper implements SerialPortEventListener {
	SerialPort serialPort;
        /** The port we're normally going to use. */
	private static final String PORT_NAMES[] = { 
			"/dev/tty.usbserial-A9007UX1", // Mac OS X
            "/dev/ttyACM0", // Raspberry Pi
			"/dev/ttyUSB0", // Linux
			"COM3", // Windows
	};
	/**
	* A BufferedReader which will be fed by a InputStreamReader 
	* converting the bytes into characters 
	* making the displayed results codepage independent
	*/
	private BufferedReader input;
	/** The output stream to the port */
	//private OutputStream output;
	/** Milliseconds to block while waiting for port open */
	private static final int TIME_OUT = 2000;
	/** Default bits per second for COM port. */
	private static final int DATA_RATE = 9600;
	
	private static int count;
	private static double sum;
	Timer timer;
	boolean flag;
	
	// MQTT
	private static String topic = "home/mainroom/usage/";
	private static String broker = "tcp://192.168.1.46:1883";
	private static String clientId = "currentSensor";
	private static int qos = 2;
	private static MemoryPersistence persistence;
	private static MqttClient client;
	private static MqttConnectOptions connOpts;

	public void initialize() {
                
		// the next line is for Raspberry Pi and                 
		// gets us into the while loop and was suggested here was suggested http://www.raspberrypi.org/phpBB3/viewtopic.php?f=81&t=32186
                
		System.setProperty("gnu.io.rxtx.SerialPorts", "/dev/ttyACM0");

		CommPortIdentifier portId = null;
		@SuppressWarnings("rawtypes")
		Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();

		//First, Find an instance of serial port as set in PORT_NAMES.
		while (portEnum.hasMoreElements()) {
			CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
			for (String portName : PORT_NAMES) {
				if (currPortId.getName().equals(portName)) {
					portId = currPortId;
					break;
				}
			}
		}
		if (portId == null) {
			System.out.println("Could not find COM port.");
			return;
		}
		System.out.println("starting publisher");
		flag = initMQTT();
		System.out.println("publisher setup");
		if(flag != true){return;}
		System.out.println("starting serial reader");
		try {
			// open serial port, and use class name for the appName.
			serialPort = (SerialPort) portId.open(this.getClass().getName(),
					TIME_OUT);

			// set port parameters
			serialPort.setSerialPortParams(DATA_RATE,
					SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);

			// open the streams
			input = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
			//output = serialPort.getOutputStream();

			// add event listeners
			serialPort.addEventListener(this);
			serialPort.notifyOnDataAvailable(true);
		} catch (Exception e) {
			System.err.println(e.toString());
		}
	}
	
	public boolean initMQTT(){
		try{
			persistence = new MemoryPersistence();
			client = new MqttClient(broker, clientId, persistence);
			connOpts = new MqttConnectOptions();
			connOpts.setCleanSession(true);
			client.connect(connOpts);
			
		} catch(MqttException me) {
            System.out.println("reason "+me.getReasonCode());
            System.out.println("msg "+me.getMessage());
            System.out.println("loc "+me.getLocalizedMessage());
            System.out.println("cause "+me.getCause());
            System.out.println("excep "+me);
            me.printStackTrace();
            return false;
        }
		return true;
	}
	
	// publish to mqtt server
	
	public void publish(String text) throws InterruptedException {
    	if (text==null){return;}
        try {
        	MqttMessage message = new MqttMessage(text.getBytes());
        	message.setQos(qos);
        	client.publish(topic, message);
        } catch(MqttException me) {
            System.out.println("reason "+me.getReasonCode());
            System.out.println("msg "+me.getMessage());
            System.out.println("loc "+me.getLocalizedMessage());
            System.out.println("cause "+me.getCause());
            System.out.println("excep "+me);
            me.printStackTrace();
        }
    }

	/**
	 * This should be called when you stop using the port.
	 * This will prevent port locking on platforms like Linux.
	 */
	public synchronized void close() {
		if (serialPort != null) {
			serialPort.removeEventListener();
			serialPort.close();
		}
		try {
			client.disconnect();
		} catch (MqttException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

	/**
	 * Handle an event on the serial port. Read the data and print it.
	 */
	public synchronized void serialEvent(SerialPortEvent oEvent) {
		if (oEvent.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
			try {
				String inputLine=input.readLine();
				//System.out.println(inputLine);
				sum += Double.valueOf(inputLine);
				count ++;
			} catch (Exception e) {
				//System.out.println("derp");
			}
		}
		// Ignore all the other eventTypes, but you should consider the other ones.
	}
	
	public static void main(String[] args) throws Exception {
		CMHelper main = new CMHelper();
		main.initialize();
		main.run();
	}
	
	public void run() {
		try {
			timer = new Timer();
	        timer.scheduleAtFixedRate(new RemindTask(),new Date(),5000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	class RemindTask extends TimerTask {
        public void run() {
        	String text;
        	if (count == 0){
        		System.out.println("count is 0");
        	}
        	else {
        		System.out.print("average: ");
        		System.out.printf("%.2f",(sum/count));
        		System.out.println(" count: "+count);
        		try {
        			text = String.format("%.2f",(sum/count));
					publish(text);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
        	}
            sum = 0;
            count = 0;
        }
    }
}