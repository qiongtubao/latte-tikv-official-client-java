package latte.monitor;

import com.google.common.collect.Maps;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import latte.lib.api.monitor.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tikv.common.TiSession;
import org.tikv.common.log.SlowLog;
import org.tikv.common.log.SlowLogSpan;

public class SlowLogDelegate implements SlowLog {
  public static Logger logger = LoggerFactory.getLogger(SlowLogDelegate.class);
  public Monitor monitor;
  public SlowLog slowLog;

  public Map<String, Object> fields = Maps.newConcurrentMap();

  public Throwable throwable;

  public TiSession session;

  public SlowLogDelegate(Monitor monitor, SlowLog slowLog, TiSession session) {
    this.monitor = monitor;
    this.slowLog = slowLog;
    this.session = session;
  }

  List<SlowLogSpanDelegate> spans = new LinkedList<>();

  @Override
  public SlowLogSpan start(String name) {
    SlowLogSpanDelegate span =
        new SlowLogSpanDelegate(
            monitor.getTransaction("tikv.remoting." + name), slowLog.start(name));
    spans.add(span);
    return span;
  }

  @Override
  public long getTraceId() {
    return this.slowLog.getTraceId();
  }

  @Override
  public long getThresholdMS() {
    return this.slowLog.getThresholdMS();
  }

  @Override
  public void setError(Throwable err) {
    this.slowLog.setError(err);
    spans.forEach(
        span -> {
          span.setError(err);
        });
  }

  @Override
  public SlowLog withFields(Map<String, Object> fields) {
    this.fields.putAll(fields);
    this.slowLog.withFields(fields);
    return this;
  }

  @Override
  public Object getField(String key) {
    return fields.get(key);
  }

  @Override
  public void log() {
    this.slowLog.log();
  }

  static Monitor latteMonitor = null;

  public static SlowLog create(SlowLog slowLog, TiSession session) {
    if (latteMonitor == null) {
      return slowLog;
    }
    return new SlowLogDelegate(latteMonitor, slowLog, session);
  }

  public static void setMonitor(Monitor monitor) {
    latteMonitor = monitor;
  }
}
