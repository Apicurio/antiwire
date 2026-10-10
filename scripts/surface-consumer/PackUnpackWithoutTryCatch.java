// surface-expect: port=pass
import com.squareup.wire.AnyMessage;
import com.squareup.wire.Message;
import com.squareup.wire.ProtoAdapter;

/** AnyMessage.pack and unpack declare no checked exception upstream (no @Throws). */
public class PackUnpackWithoutTryCatch {
  static AnyMessage pack(Message<?, ?> message) {
    return AnyMessage.Companion.pack(message);
  }

  static <T> T unpack(AnyMessage any, ProtoAdapter<T> adapter) {
    return any.unpack(adapter);
  }
}
