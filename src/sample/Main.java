package sample;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import purejavacomm.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


// to use the library RXTX go to this website: http://fizzed.com/oss/rxtx-for-java to download the lib
// copy the .dll file to Java->bin and .jar file to Java->lib->ext

public class Main extends Application {
    Stage window;
    int data;
    byte[] buffer = new byte[1024];
    Serial sr=new Serial();
    ComboBox<String> comboBox;
    TextField text;
    Slider slider = new Slider(1,100,1);
    SerialPort serialPort;
    OutputStream outputStream;
    InputStream inputStream;

    ObservableList<XYChart.Data<String, Integer>> xyList1 = FXCollections.observableArrayList();
    ObservableList<String> myXaxisCategories = FXCollections.observableArrayList();
    private Task<Date> task;
    private LineChart<String,Number> lineChart;
    private XYChart.Series xySeries1;
    private CategoryAxis xAxis;
    private int lastObservedSize;

    @Override
    public void start(Stage primaryStage) throws Exception{
        window = primaryStage;

        xyList1.addListener((ListChangeListener<XYChart.Data<String, Integer>>) change -> {
            if (change.getList().size() - lastObservedSize > 1000) {
                lastObservedSize += 1000;
                xAxis.getCategories().remove(0, 1000);
            }
        });

        xAxis = new CategoryAxis();
        xAxis.setLabel("Time");

        final NumberAxis yAxis = new NumberAxis();
        lineChart = new LineChart<>(xAxis,yAxis);

        lineChart.setTitle("Temperature over Time");
        lineChart.setAnimated(false);
        task = new Task<Date>() {
            @Override
            protected Date call() throws Exception {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException iex) {
                        Thread.currentThread().interrupt();
                    }
                    if (isCancelled()) {
                        break;
                    }
                    updateValue(new Date());
                }
                return new Date();
            }
        };

        task.valueProperty().addListener(new ChangeListener<Date>() {
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            Random random = new Random();

            @Override
            public void changed(ObservableValue<? extends Date> observableValue, Date oldDate, Date newDate) {
                String strDate = dateFormat.format(newDate);
                myXaxisCategories.add(strDate);

                xyList1.add(new XYChart.Data(strDate,data));
            }
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(task);

        xAxis.setCategories(myXaxisCategories);

        xySeries1 = new XYChart.Series(xyList1);
        lineChart.getData().add(xySeries1);
        //
        Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();
        int i = 0;
        String[] r = new String[5];
        while (portEnum.hasMoreElements() && i < 5) {
            CommPortIdentifier portIdentifier = portEnum.nextElement();
            r[i] = portIdentifier.getName();//+  " - " +  getPortTypeName(portIdentifier.getPortType()) ;
            i++;
        }

        comboBox = new ComboBox<>(FXCollections.observableArrayList(r));
        comboBox.valueProperty().addListener(e->{
            Object selectedItem = comboBox.getValue();
            String com = selectedItem.toString();
            Serial sr=new Serial();
            try {
                sr.ser(com);
            } catch (TooManyListenersException e1) {
                e1.printStackTrace();
            }
        });
//        comboBox.setPromptText("Select Port to Connect");
        //Button to send out data
        Button sendButton = new Button("Send");
//        sendButton.setOnAction(e->sendData());
        final double INIT_VALUE = 50;
        text = new TextField();
        text.setMaxWidth(100);
        text.setText(Double.toString(INIT_VALUE));

        slider.setMin(0);
        slider.setMax(100);
        slider.setValue(INIT_VALUE);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setBlockIncrement(1);
        text.textProperty().bindBidirectional(slider.valueProperty(), NumberFormat.getNumberInstance());
        slider.valueProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number t1) {
                try {
                    outputStream = serialPort.getOutputStream();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                new SerialWriter(outputStream);
            }
        });

        //Layout
        VBox layout = new VBox(10);
        layout.setAlignment(Pos.CENTER);
        layout.getChildren().addAll(comboBox,slider,text,sendButton,lineChart);

        Scene scene = new Scene(layout,300,250);
        window.setScene(scene);
        window.show();
    }
    void connect( String portName ) throws Exception {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if ( portIdentifier.isCurrentlyOwned() ) {
            System.out.println("Error: Port is currently in use");
        }
        else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);

            if ( commPort instanceof SerialPort ) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(57600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);

                InputStream in = serialPort.getInputStream();
                OutputStream out = serialPort.getOutputStream();
                serialPort.addEventListener(new SerialReader(in));
                serialPort.notifyOnDataAvailable(true);
            }
            else {
                System.out.println("Error: Only serial ports are handled by this example.");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        launch(args);
    }

    public class Serial{
        Enumeration portList;
        CommPortIdentifier portId;
        String messageString = "0";


        private String ser(String com) throws TooManyListenersException {
            portList=CommPortIdentifier.getPortIdentifiers();
            while(portList.hasMoreElements()){
                portId = (CommPortIdentifier) portList.nextElement();
                if(portId.getPortType() == CommPortIdentifier.PORT_SERIAL)
                    if(portId.getName().equals(com)){
                        try{
                            serialPort = (SerialPort)portId.open("DemoApp", 5000);
                        }catch(PortInUseException e){
                            return "Port In Use";
                        }
                        try{
                            inputStream = serialPort.getInputStream();
                            outputStream = serialPort.getOutputStream();
                        }catch(IOException e){
                            return "Error!!!!!!!";
                        }
                        try{
                            serialPort.setSerialPortParams(9600,
                                    SerialPort.DATABITS_8,
                                    SerialPort.STOPBITS_1,
                                    SerialPort.PARITY_NONE);
                        }catch(UnsupportedCommOperationException e){}
                        serialPort.addEventListener(new SerialReader(inputStream));
                        serialPort.notifyOnDataAvailable(true);
                    }
            }
            return "Configured successfully";
        }
        private void close(){
            serialPort.close();
        }

    }
    public class SerialReader implements SerialPortEventListener {
        private InputStream in;


        public SerialReader ( InputStream in ) {
            this.in = in;
        }
        public void serialEvent(SerialPortEvent arg0) {
            try {
                data = inputStream.read();
                System.out.println("this is the :"+data);
            } catch ( IOException e ) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    /** */
    public class SerialWriter {
        public SerialWriter ( OutputStream out ) {
            outputStream = out;
            try{
                int a = (int)slider.getValue();
                char f = 'f';
                byte[] byteArray = new byte[2];
                byteArray[0] = (byte)f;
                byteArray[1] = (byte)a;
                outputStream.write(byteArray);
            }catch( IOException e ) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

}


