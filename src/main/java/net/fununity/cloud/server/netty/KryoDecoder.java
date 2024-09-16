package net.fununity.cloud.server.netty;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.ByteArrayInputStream;
import java.util.List;

public class KryoDecoder extends ByteToMessageDecoder {
	private final Kryo kryo = new Kryo();

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		// Check if the length field is received
		if (in.readableBytes() < 4) {
			return;
		}

		in.markReaderIndex();
		int length = in.readInt();

		if (in.readableBytes() < length) {
			in.resetReaderIndex();
			return;
		}

		byte[] bytes = new byte[length];
		in.readBytes(bytes);

		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
		Input input = new Input(byteArrayInputStream);
		Object obj = kryo.readClassAndObject(input);
		input.close();

		out.add(obj);
	}
}
