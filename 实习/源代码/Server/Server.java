import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.io.BufferedReader;
import java.time.format.DateTimeFormatter;
public class Server {
    public static boolean flag=false;//服务器端的状态
    private static ServerSocket serverSocket;
    private static final int PORT = 12345; // 服务器监听的端口号
    static ArrayList<Link> clients = new ArrayList<>(); // 存储所有客户端连接
    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");//创建了一个格式化器，用于格式化或解析时间为小时、分钟和秒的格式

    public static void main(String[] args) {
        try {

            // 检查是否提供了日志文件路径参数
            if (args.length < 1) {
                System.err.println("必须指定日志文件的路径");
                System.exit(1);
            }
            File logFile = new File(args[0]);
            if (!logFile.exists()) {
                System.err.println("您给出的日志文件不存在，请查看");
                System.exit(1);
            }
            // 初始化日志文件
            if (!Log.createWriter(logFile)) {
                System.err.println("日志文件初始化失败");
                System.exit(1);
            }
            System.out.println("日志文件流创建成功，可以接收系统运行日志了，全名大聊天正式开始！\n");
            System.out.println("如果想结束聊天服务器输入指令：end");

            Log.append("["+ LocalDateTime.now().format(formatter)+"]"+"系统启动----");
            serverSocket = new ServerSocket(PORT);
            Control control=new Control(serverSocket);
            Inspection inspection=new Inspection(serverSocket);
            control.start();
            inspection.start();
            flag=true;
            while (flag) {
                Socket socket = serverSocket.accept(); // 接受客户端连接
                Link client = new Link(socket);
                clients.add(client);
                client.start();

            }
        } catch (IOException e) {
            if (e.getMessage().equals("Socket closed")) {
//                Log.append("服务器正常关闭: " + e.getMessage());
            } else {
                System.out.println("服务器异常: " + e.getMessage());
                Log.append("服务器异常: " + e.getMessage());
            }
        } finally {
            Log.closeWriter();
        }
    }
}
class Inspection extends Thread {
    private ServerSocket serverSocket;
    public Inspection(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }
    public void run() {
            try {
                // 模拟检查服务器状态，这里我们只是简单地监视当前进程
                Thread.sleep(10000); // 每10秒检查一次
            } catch (InterruptedException e) {
                System.out.println("Inspection thread interrupted");
                Log.append("监视线程被中断");
            }
    }
}
class Control extends Thread {
    private ServerSocket serverSocket;

    public Control(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }


    @Override
    public void run() {
        try (BufferedReader keyboardInput = new BufferedReader(new InputStreamReader(System.in))) {
            String command;
            boolean flag_valid=false;//是否输入有效命令
            while ((command = keyboardInput.readLine()) != null) {
//                switch (command.toLowerCase())
                    if(command.equals("end")) {
                        flag_valid=true;
                        Server.flag = false;
                        for (Link client : Server.clients) {
                            client.closeConnection();
                        }
                        serverSocket.close(); // 关闭ServerSocket将导致server主线程解除阻塞状态并退出
                        System.out.println("聊天已终止，已与所有聊天客户端断开连接");
                        return;
                    } else{
                for(Link client: Server.clients) {
                    String kick_order="kick out:"+client.nickname;
                    if(command.equals(kick_order)){
                        flag_valid=true;
                        String KickMessage="管理员已将"+client.nickname+"踢出聊天室";
                        Link.updateAllSenders(KickMessage);
                        Log.append(KickMessage); // 公告踢出
                        client.sender.getserverToClient().println("@XdY#kickout");
                        client.closeConnection();
                        System.out.println(KickMessage);
                    }
                }
                        //其他语句视为未知命令
                        if(!flag_valid)System.out.println("未知命令: " + command);
                }

            }
        } catch (IOException e) {
            System.out.println("Control thread I/O error: " + e.getMessage());
            Log.append("Control线程I/O错误：" + e.getMessage());
        }
    }
}

//在Link中实现
class Anteroom extends Thread {
//    private Link link;
//
//    public Anteroom(Link link) {
//        this.link = link;
//    }
//
//    public void run() {
//    }
}
class Link extends Thread {
    Socket socket = null;
    String nickname = null;
    boolean flagContinue = false;
    Receiver receiver = null;
    Sender sender = null;
    ConcurrentLinkedDeque<String> messagesToSend = new ConcurrentLinkedDeque<>(); // 在这里直接初始化，必须在receiver和sender前面初始化
    DateTimeFormatter formatter =Server.formatter;
    public static void updateAllSenders(String primMessage) {
        synchronized (Server.clients) {
            for (Link client : Server.clients) {
                client.sendMessageToClient(primMessage);
            }
        }
    }//确保了在检查客户端列表时的线程安全，避免了数据不一致(个线程正在检查列表，而另一个线程修改了列表\两个线程可能同时检查列表并发现昵称未被使用，结果导致两个客户端被分配相同的昵称)
    public void sendMessageToClient(String message) {
        synchronized (messagesToSend) {
            messagesToSend.add(message);
            messagesToSend.notify(); // 唤醒正在等待的发送线程
        }
    }



    public Link(Socket socket) {
        this.socket = socket;
        this.flagContinue = true;
        this.receiver = new Receiver(this);
        this.sender = new Sender(this);
        this.receiver.start();
        this.sender.start();
    }
    public void closeConnection() {
        try {
            this.flagContinue = false; // 标记不再继续
            synchronized (messagesToSend) {
                messagesToSend.notifyAll();  // 唤醒所有等待的线程
            }

//            if (this.receiver != null) this.receiver.interrupt();
//            if (this.sender != null) this.sender.join();  // 主线程等待 sender 线程结束
            if (this.receiver != null) {
                this.receiver.interrupt(); // 通知 receiver 线程中断
                this.receiver.join();      // 等待 receiver 线程结束
            }
            if (this.sender != null) {
                this.sender.interrupt();   // 通知 sender 线程中断
                this.sender.join();        // 等待 sender 线程结束
            }
            this.socket.close();       // 关闭Socket
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
            Log.append(e.getMessage());
        }catch (InterruptedException e) {
            if(e.getMessage()!=null) {
                System.err.println("ioe:" + e.getMessage());
            }
        }
    }




    //    Receiver 负责从客户端读取信息并将其添加到队列 messagesToSend
    class Receiver extends Thread {
        private Link link;

        public Receiver(Link link) {
            this.link = link;
        }
        public static synchronized boolean isNicknameDuplicate(String nickname) {
            for (Link client : Server.clients) {
                if (client.nickname != null && client.nickname.equalsIgnoreCase(nickname)) {
                    return true;
                }//重名或者名字为空返回false，注意：这里重名检测不区分大小写
            }
            return false;
        }

        @Override
        public void run() {
            try {
                BufferedReader clientInput = new BufferedReader(new InputStreamReader(link.socket.getInputStream()));
                String proposedNickname = clientInput.readLine();
                if (isNicknameDuplicate(proposedNickname)) {
                    PrintWriter out = new PrintWriter(link.socket.getOutputStream(), true);
                    out.println("@XdY#duplicationName");//_endMask
                    link.socket.close();
                    return; // 结束线程
                }//检测到重名则断开连接
                link.nickname = proposedNickname;
//                link.nickname=clientInput.readLine();
                String welcomeMessage="["+LocalDateTime.now().format(formatter)+"]"+"欢迎"+link.nickname+" 进入聊天室！";
                Link.updateAllSenders(welcomeMessage);
                System.out.println(welcomeMessage);
                Log.append(welcomeMessage); // 公告进入
                String message;
                while ((message = clientInput.readLine()) != null) {
                    if(message.equals("@XdY#endChatting")){//endMask
                        String ByeMessage=link.nickname+" 退出了聊天室 ";
                        Link.updateAllSenders(ByeMessage);
                        Log.append(ByeMessage); // 公告退出
                        System.out.println("["+LocalDateTime.now().format(Server.formatter) + "] " +ByeMessage);
                    }
                    else {
                        String formattedMessage = "[" + link.nickname + ": " + LocalDateTime.now().format(Server.formatter) + "] " + message;
                        Link.updateAllSenders(formattedMessage);
                        Log.append(formattedMessage); // 记录日志
                        System.out.println(formattedMessage);
                    }
                }
            }catch (IOException e) {
                if (e.getMessage().equals("Socket closed")) {
//                Log.append("服务器正常关闭: " + e.getMessage());
                } else {
                    System.out.println("服务器异常: " + e.getMessage());
                    Log.append("服务器异常: " + e.getMessage());
                }
            } finally {
                link.closeConnection();  // 确保连接被关闭
                Server.clients.remove(link);  // 从客户端列表中移除
            }
        }
    }

//    Sender 等待 messagesToSend 队列中的消息，一旦有消息就发送给客户端
    class Sender extends Thread {
        private Link link;
        private PrintWriter serverToClient;
        public PrintWriter getserverToClient(){
            return  this.serverToClient;
        }

        public Sender(Link link) {
            this.link = link;
            try {
                this.serverToClient = new PrintWriter(link.socket.getOutputStream(), true);
            } catch (IOException e) {
                Log.append("创建发送流失败：" + e.getMessage());
                System.out.println("创建发送流失败：" + e.getMessage());
            }
        }//创建发送流

    @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    String messageToSend = null;
                    synchronized (link.messagesToSend) {
                    while (link.messagesToSend.isEmpty() && link.flagContinue) {
                        link.messagesToSend.wait();//只有在 flagContinue 为真（正在运行时）时等待
                    }
                    if (!link.flagContinue && link.messagesToSend.isEmpty()) {
                            break;  // 如果不再继续且没有消息要发送，退出循环
                    }
                    messageToSend = link.messagesToSend.poll();//获取并移除队列的头元素，每次调用 poll 方法，队列的头元素都会被移除
                    }
                if (messageToSend != null) {
                    serverToClient.println(messageToSend);
                }
            }
        } catch (InterruptedException e) {
            System.out.println("Sender thread interrupted.");
        } catch (Exception e) {
            System.out.println("Error in sender thread: " + e.getMessage());
            Log.append("发送数据时错误：" + e.getMessage());
        } finally {
            serverToClient.close();
        }
    }
}

}
class Log {
    static FileWriter writer;

    public static boolean createWriter(File file) {// 以追加模式打开指定文件
        try {
            writer = new FileWriter(file, true);
            return true;
        } catch (IOException e) {
            System.err.println("无法创建日志文件: " + e.getMessage());
            return false;
        }
    }

    public static boolean append(String content) {//将content写入日志文件
        if (writer == null) return false; // 检查writer是否已经关闭
        try {
            writer.append(content + "\n");
            writer.flush();//确保数据立即发送或记录
            return true;
        } catch (IOException e) {
            System.err.println("写入日志文件时发生错误: " + e.getMessage());
            return false;
        }
    }

    public static boolean closeWriter() {
        try {
            if (writer != null) {
                writer.close();
            }
            return true;
        } catch (IOException e) {
            System.err.println("关闭日志文件时发生错误: " + e.getMessage());
            return false;
        }
    }
}





