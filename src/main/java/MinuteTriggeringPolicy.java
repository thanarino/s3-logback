import ch.qos.logback.core.rolling.DefaultTimeBasedFileNamingAndTriggeringPolicy;

import java.util.Calendar;
import java.util.Date;

public class MinuteTriggeringPolicy<E> extends DefaultTimeBasedFileNamingAndTriggeringPolicy<E> {

    protected int periodMinutes = 1;

    public int getPeriodMinutes() {
        return this.periodMinutes;
    }

    public void setPeriodMinutes(int minutes) {
        if (minutes > 0) {
            this.periodMinutes = minutes;
        }
    }

    private long roundTimestamp(long time) {
        int periodTicks = periodMinutes * 60000;
        return periodTicks * (time / periodTicks);
    }

    @Override
    public void setDateInCurrentPeriod(long now) {
        dateInCurrentPeriod.setTime(roundTimestamp(now));
    }

    @Override
    public void setDateInCurrentPeriod(Date _dateInCurrentPeriod) {
        dateInCurrentPeriod = new Date(roundTimestamp(_dateInCurrentPeriod.getTime()));
    }

    @Override
    public void computeNextCheck() {
        rc.setTime(dateInCurrentPeriod);
        rc.set(Calendar.SECOND, 0);
        rc.set(Calendar.MILLISECOND, 0);
        rc.add(Calendar.MINUTE, periodMinutes);

        nextCheck = rc.getTime().getTime();
    }

    @Override
    public String toString() {
        return "MinuteTriggeringPolicy";
    }


}
