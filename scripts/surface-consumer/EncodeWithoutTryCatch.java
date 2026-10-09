// surface-expect: port=fail owner=TASK-33.3 symbol=IOException reason=Message.encode() declares throws IOException in the port; upstream Kotlin declares none, so callers without try/catch do not compile
import com.squareup.wire.Message;

/** Kotlin has no checked exceptions; Java callers of Wire never wrote try/catch around encode(). */
public class EncodeWithoutTryCatch {
  static byte[] use(Message<?, ?> message) {
    return message.encode();
  }
}
