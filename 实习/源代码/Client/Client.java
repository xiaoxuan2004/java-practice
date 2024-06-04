import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.BufferedReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Client extends JFrame implements ActionListener, WindowListener{
    public static final String _specialMark = "@XdY#";
    public static final String _endMark = _specialMark + "endChatting";
    public static final String _duplicationName = _specialMark + "duplicationName";
    public static final String _kickout = _specialMark + "kickout";
    public static final int _basicTimeGap = 25;

    boolean flagContinue = false;

    Sender sender = null;
    Receiver receiver = null;
    Socket socket = null;
    PrintWriter clientToServer = null;
    BufferedReader serverToClient = null;
    String messageToSend = null;

    JTextField fieldIP = new JTextField();
    JTextField fieldPort = new JTextField();
    JTextField fieldNickname = new JTextField();
    JButton buttonLogin = new JButton("进入聊天室");
    JButton buttonLogout = new JButton("退出聊天室");
    JTextArea areaContent = new JTextArea();
    JTextArea areaMessage = new JTextArea(5, 20);
    JButton buttonSend = new JButton("发送");

    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public Client() {
        //初始化GUI界面
        setTitle("全民大讨论聊天客户端-----220404218计算机223肖璇开发");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);
        // 设置文本框长度
        fieldIP = new JTextField(15);  // IP地址输入框，假设设置为15列宽
        fieldPort = new JTextField(10);  // 端口输入框，假设设置为5列宽
        fieldNickname = new JTextField(10);  // 昵称输入框，假设设置为10列宽

        JPanel northPanel = new JPanel();
        northPanel.add(new JLabel("IP:"));
        northPanel.add(fieldIP);
        northPanel.add(new JLabel("端口:"));
        northPanel.add(fieldPort);
        northPanel.add(new JLabel("昵称:"));
        northPanel.add(fieldNickname);
        northPanel.add(buttonLogin);
        northPanel.add(buttonLogout);

        JPanel CenterPanel = new JPanel(new BorderLayout());
        areaContent.setEditable(false);
        CenterPanel.add(new JScrollPane(areaContent), BorderLayout.CENTER);


        JPanel SouthPanel = new JPanel(new BorderLayout());
        SouthPanel.add(new JScrollPane(areaMessage), BorderLayout.CENTER);
        SouthPanel.add(buttonSend, BorderLayout.EAST);


        add(northPanel, BorderLayout.NORTH);
        add(CenterPanel, BorderLayout.CENTER);
        add(SouthPanel,BorderLayout.SOUTH);

        buttonLogin.addActionListener(this);
        buttonLogout.addActionListener(this);
        buttonSend.addActionListener(this);
        //初始化时退出聊天按钮无效
        buttonLogout.setEnabled(false);

        addWindowListener(this);
        setVisible(true);
    }

    @Override public void windowOpened(WindowEvent e) { }
    @Override public void windowClosed(WindowEvent e) { }
    @Override public void windowIconified(WindowEvent e) { }
    @Override public void windowDeiconified(WindowEvent e) { }
    @Override public void windowActivated(WindowEvent e) { }
    @Override public void windowDeactivated(WindowEvent e) { }
//    @Override public void windowClosing(WindowEvent e) {
//        flagContinue = false;
//        try {
//            //关闭之前先检查输入输出流和套接字是否已关闭（已退出） 若未关闭则关闭
//            if (serverToClient != null) {
//                serverToClient.close();
//            }
//            if (clientToServer != null) {
//                clientToServer.close();
//            }
//            if (socket != null) {
//                socket.close();
//            }
////                serverToClient.close();
////
////                clientToServer.close();
////
////                socket.close();
//
//            messageToSend = _endMark;
//            Thread.sleep(_basicTimeGap * 10);
//        } catch (IOException ioe) {
//        } catch (InterruptedException ie) {
//        }
//    }

//创建并启动了一个新线程，目的是避免在事件分派线程（负责处理GUI事件）中执行可能耗时的操作
// 如果在休眠期间线程被中断，它会重新设置中断状态，允许线程优雅地结束
@Override public void windowClosing(WindowEvent e) {
    flagContinue = false; // 确保后台线程停止
    try {
        // 发送退出标志
        if (clientToServer != null) {
            clientToServer.println(_endMark);
        }

        if (socket != null) {
            socket.shutdownOutput(); //关闭输出流
        }//通过先关闭输出流，告诉对方通信已经结束，但允许对方仍然可以发送数据，从而有序关闭连接

        // 等待一段时间后关闭资源
        new Thread(() -> {
            try {
                Thread.sleep(_basicTimeGap * 10);
                if (serverToClient != null) {
                    serverToClient.close();
                }
                if (clientToServer != null) {
                    clientToServer.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // 重新设置中断状态
            } catch (IOException ioe) {
                System.err.println("Error closing resources: " + ioe.getMessage());
            }
        }).start();//使用 Lambda 表达式创建和启动一个新的线程。通过在一个新的线程中执行资源关闭操作，可以避免在主线程中执行这些操作时阻塞主线程
    } catch (IOException ioe) {
        System.err.println("Error preparing to close connection: " + ioe.getMessage());
    }
}
    private void connect() {
        try {//建立client和server的socket
            socket = new Socket(fieldIP.getText(), Integer.parseInt(fieldPort.getText()));
            clientToServer = new PrintWriter(socket.getOutputStream(), true);
            serverToClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            flagContinue=true;
            sender = new Sender(this);
            receiver = new Receiver(this);
            sender.start();
            receiver.start();
            String Nickname=fieldNickname.getText();
            clientToServer.println(Nickname);//首先发送昵称
            JOptionPane.showMessageDialog(this, "已经与聊天室服务器建立连接", "消息", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "连接错误！" + e.getMessage(), "消息", JOptionPane.ERROR_MESSAGE);
            areaContent.append("连接错误: " + e.getMessage() + "\n");
        }
    }

    @Override public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == buttonLogin) {
            buttonLogin.setEnabled(false);
            buttonLogout.setEnabled(true);
            connect();
        } else if (ae.getSource() == buttonLogout) {
            buttonLogin.setEnabled(true);
            buttonLogout.setEnabled(false);
            closeConnection();
        } else if (ae.getSource() == buttonSend) {
            messageToSend=areaMessage.getText();
            if(messageToSend!=null) {
                areaMessage.setText(""); // 发送后清空文本区域
            }
        }
    }
    private void closeConnection() {//与重名不同，离开聊天室是用户主动结束，此时用户点击按钮后，若传给messagetosend再广播，则不该在用户端立即关闭，而是主机再发一个退出指令后用户接受到退出
        try {//而这里 主动退出设置的是直接向输出流发送，主机收到后同步广播离开信息，用户端套接字等待0.1s后，接受完同步信息后，用户关闭套接字
//           messageToSend=_endMark;
            clientToServer.println(_endMark);//向服务器发送退出请求
            flagContinue=false;//停止sender和receiver
            if (serverToClient != null) {
                Thread.sleep(100);
                serverToClient.close();
            }
            if (clientToServer != null) {
                Thread.sleep(100);
                clientToServer.close();
            }
            if (socket != null) {
                Thread.sleep(100);
                socket.close();
            }//关闭输入输出流，关闭套接字

            // 重置客户端状态
            clientToServer = null;
            serverToClient = null;
            socket = null;
            String Nickname=fieldNickname.getText();
            JOptionPane.showMessageDialog(this, "已断开连接！", "信息", JOptionPane.INFORMATION_MESSAGE);
//            areaContent.append(Nickname+"退出了聊天室\n");
        }catch (IOException ioe) {
            JOptionPane.showMessageDialog(this, "关闭连接错误: " + ioe.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
//            areaContent.append("关闭连接错误: " + ioe.getMessage() + "\n");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public class Sender extends Thread {
        Client client = null;
        public Sender(Client client) {
            this.client = client;
        }
        @Override
        public void run() {
            try {
                while (client.flagContinue) {
                    if (client.messageToSend != null && client.messageToSend.trim().length()!=0) {
                        client.clientToServer.println(client.messageToSend);
                        client.messageToSend = null; // 在发送后重置
                    }
                    try {
                        Thread.sleep(Client._basicTimeGap);
                    } catch (InterruptedException ie) {
                        System.out.println("Sender Thread Interrupted: " + ie.getMessage());
                    }
                }
            } catch (Exception e) {
            }
        }
    }
    public class Receiver extends Thread {
        Client client = null;
        public Receiver(Client client) {
            this.client = client;
        }
        @Override
        public void run() {
            try {
                String messageFromServer;
                while (client.flagContinue) {
                    messageFromServer = client.serverToClient.readLine();
                    if( messageFromServer!= null)
                    {
                        if (messageFromServer.equals(Client._duplicationName)) {//当重名时，客户建立连接后首次发出昵称，主机检查到重名后立即发送一条重名指令，两边socket关闭
                            buttonLogin.setEnabled(true);
                            buttonLogout.setEnabled(false);
                            JOptionPane.showMessageDialog(null, "昵称重复，请使用其他昵称。", "错误", JOptionPane.ERROR_MESSAGE);
                            client.flagContinue = false;
                            break;
                        }
                        else if(messageFromServer.equals(Client._kickout)){
                            buttonLogin.setEnabled(false);
                            buttonLogout.setEnabled(false);
                            JOptionPane.showMessageDialog(null, "您已被管理员踢出群聊，请遵守聊天室规则", "错误", JOptionPane.ERROR_MESSAGE);
                            client.flagContinue = false;
                            break;
                        }
                        client.areaContent.append(messageFromServer + "\n");
                    }
                }
            } catch (IOException e) {
                System.out.println("Error Reading From Server: " + e.getMessage());
                e.printStackTrace();//打印异常堆栈信息
            } finally {
                try {
                    if (serverToClient != null) {
                        serverToClient.close();
                    }
                    if (clientToServer != null) {
                        clientToServer.close();
                    }
                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException e) {
                    System.out.println( e.getMessage());
                }
            }
        }
    }


    public static void main(String[] args) {
        Client client = new Client();
    }
}

