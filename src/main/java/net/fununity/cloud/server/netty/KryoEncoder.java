package net.fununity.cloud.server.netty;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ByteArrayOutputStream;

public class KryoEncoder extends MessageToByteEncoder<Object> {
	private final Kryo kryo = new Kryo();

	@Override
	protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		Output output = new Output(byteArrayOutputStream);
		kryo.writeClassAndObject(output, msg);
		output.close();

		byte[] bytes = byteArrayOutputStream.toByteArray();
		out.writeInt(bytes.length);  // Write the length of the object
		out.writeBytes(bytes);       // Write the object
	}
}
