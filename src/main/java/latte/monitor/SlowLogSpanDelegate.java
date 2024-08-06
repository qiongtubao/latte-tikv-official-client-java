package latte.monitor;

import com.google.gson.JsonElement;
import latte.lib.api.monitor.Transaction;
import org.tikv.common.log.SlowLogSpan;

public class SlowLogSpanDelegate implements SlowLogSpan {
  public Transaction transaction;
  public SlowLogSpan span;

  public SlowLogSpanDelegate(Transaction transaction, SlowLogSpan span) {
    this.transaction = transaction;
    this.span = span;
  }

  @Override
  public void addProperty(String key, String value) {
    this.transaction.addTag(key, value);
    this.span.addProperty(key, value);
  }

  @Override
  public void start() {
    this.span.start();
  }

  @Override
  public void end() {
    if (throwable == null) {
      this.transaction.setSuccess();
    } else {
      this.transaction.setFail(throwable);
    }
    this.transaction.complete();
    this.span.end();
  }

  @Override
  public JsonElement toJsonElement() {
    return this.span.toJsonElement();
  }

  Throwable throwable = null;

  public void setError(Throwable e) {
    throwable = e;
  }
}
