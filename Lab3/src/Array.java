import java.util.ArrayList;
import java.util.List;

public class Array extends Type {
	public String kind = "array"; // 类型为数组类型
	public String elementType; // 数组内存放的类型
	public int dimension; // 数组的维度
	public List<Integer> arrayNum_list = new ArrayList<>();//用于传数组里的值

	public Array(){
		super.kind="array";
	}
}
