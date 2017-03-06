package cn.appleye.ble;

/**
 * @author liuliaopu
 * @date 2017-02-15
 * 数据打包和解析工具
 */

public class BLEDataUtil {
    /**最大发送20个字节，但是首个字节由标志位占用*/
    private static final int MAX_SIZE = 18;

    /**开始标志*/
    private static final byte START_BYTE = 0x01;
    /**继续标志*/
    private static final byte CONTINUE_BYTE = 0x02;
    /**结束标志*/
    private static final byte END_BYTE = 0x00;

    /**
     * 字符串转为二维字节数组
     * */
    public static byte[][] encode(String strData) {
        try{
            byte[] originData = strData.getBytes("UTF-8");
            int size = (int)Math.ceil(originData.length / (MAX_SIZE*1.0));
            byte[][] data = new byte[size][MAX_SIZE+1];

            int start = 0;
            int end = 0;
            int index  = 0;
            while(index < size) {
                index ++ ;
                if(index == size) {
                    data[index-1][0] = END_BYTE;
                } else if(index == 1) {
                    data[index-1][0] = START_BYTE;
                } else {
                    data[index-1][0] = CONTINUE_BYTE;
                }

                end = Math.min(start + MAX_SIZE, originData.length);
                System.arraycopy(originData, start, data[index-1], 1, end - start);

                start = end;
            }

            return data;
        }catch(Exception e){
            e.printStackTrace();
        }

        return null;

    }

    /**
     * 字节拼接
     * */
    public static byte[] decode(byte[] data, byte[] result) {

        byte[] tempResult;

        if(result == null || isStart(data)) {
            tempResult = new byte[data.length - 1];
            System.arraycopy(data, 1, tempResult, 0, data.length-1);
        } else {
            tempResult = new byte[data.length - 1 + result.length];
            System.arraycopy(result, 0, tempResult, 0, result.length);
            System.arraycopy(data, 1, tempResult, result.length, data.length-1);
        }

        if(isEnd(data)) {
            //去掉结尾的0x00字符，避免转换成字符串乱码
            byte zeroByte = 0x00;
            int length = tempResult.length;
            for(int i=0; i<length; i++) {
                if(tempResult[length - i - 1] == zeroByte) {
                    continue;
                }

                byte[] realResult = new byte[length - i];
                System.arraycopy(tempResult, 0, realResult, 0, length - i);
                tempResult = realResult;
                break;
            }
        }

        return tempResult;
    }

    /**
     * 是否是开头
     * */
    public static boolean isStart(byte[] data){
        if(data[0] == START_BYTE) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 是否是结尾
     * */
    public static boolean isEnd(byte[] data) {
        if(data[0] == END_BYTE) {
            return true;
        } else {
            return false;
        }
    }

	/*public static void main(String[] args) {
		String data  = "{\"aaaaaaasdasdasdasdasdasdasdas\" : \"bbbbbbbbbbbbbdjashiodhasohdoahsoidhaioshdiuhasda\"}";
		//String data = "aaaaaa";
		byte[][] byteData = encode(data);
		//printData(byteData);
		byte[] result = null;
		for(int i=0; i<byteData.length; i++) {

			result = decode(byteData[i], result);
			if(isEnd(byteData[i])) {
				if(result != null) {

					try{
						String str = new String(result);
						System.out.println("result = " + str);
					}catch(Exception e){
						e.printStackTrace();
					}

				}
				break;
			}
		}
	}*/
}
