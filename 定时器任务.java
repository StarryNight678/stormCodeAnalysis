import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

class TimerTaskTest extends TimerTask {
    @Override
    public void run() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//设置日期格式
        String t = df.format(new Date());
        for (int i = 0; i < 2; i++) {
            System.out.println(t + "\t" + i);
        }
    }
}

public class Main {
    Timer timer = null;

    public Main(int time, int period) {
        timer = new Timer();
        TimerTaskTest tt = new TimerTaskTest();
        timer.schedule(tt, time * 1000, period);
    }

    public static void main(String[] args) {
        System.out.println("timer begin....");
        Main m1 = new Main(1, 500);
        System.out.println("end");
    }
}
