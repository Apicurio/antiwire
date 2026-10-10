// surface-expect: port=pass
import com.squareup.wire.Message;

/** Kotlin has no checked exceptions; Java callers of Wire never wrote try/catch around encode(). */
public class EncodeWithoutTryCatch {
  static byte[] use(Message<?, ?> message) {
    return message.encode();
  }
}
