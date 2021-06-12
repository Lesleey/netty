import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * @author Lesleey
 * @date 2021/5/30-20:10
 * @function
 */
public class Test {
    public static void main(String[] args)       {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(Unpooled.copiedBuffer("1111".getBytes()),
                Unpooled.copiedBuffer("2222".getBytes()));
        System.out.println(byteBuf.readInt());
        System.out.println(byteBuf.readInt());
        System.out.println(byteBuf.readInt());
        System.out.println(byteBuf.readInt());
    }
}
