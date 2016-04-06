package comp207p.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantInteger;

import org.apache.bcel.generic.*;



public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	ConstantPoolGen myCPGen = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public void optimize()
	{
        ClassGen cgen = new ClassGen(original);
        ConstantPoolGen cpgen = cgen.getConstantPool();
		cgen.setMajor(50);

        Method[] methods = cgen.getMethods();
        for (Method meth: methods) 
		{
			//System.out.println(meth);
            optimizeMethod(cgen, cpgen, meth);
        }

        this.optimized = cgen.getJavaClass();
	}
	
	
	private void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method) 
	{
		this.myCPGen = cpgen;
		
		Code methodCode = method.getCode();
        InstructionList instList = new InstructionList(methodCode.getCode());

        MethodGen methGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(), null, method.getName(), cgen.getClassName(), instList, cpgen);

        ConstantPool consPool = cpgen.getConstantPool();
        Constant[] constants = consPool.getConstantPool();

        InstructionHandle valueHolder;
		

		
		for (InstructionHandle handle = instList.getStart(); handle != null;)
		{
			//System.out.println(handle + "           " + method);
			if (handle.getInstruction() instanceof ArithmeticInstruction)
			{
							

				InstructionHandle toHandle = handle.getNext();
				handle = handle.getNext();
                Number lastValue = getLastPush(instList, toHandle);	
				//System.out.println(lastValue);
				int i = 0;

				if (lastValue != null) {

                    if (lastValue instanceof Integer) {
						//System.out.println(handle);
                        i = myCPGen.addInteger((int) lastValue);
                        instList.insert(handle, new LDC(i));
                    }
                    if (lastValue instanceof Float) {
                        i = myCPGen.addFloat((float) lastValue);
                        instList.insert(handle, new LDC(i));
                    }
                    if (lastValue instanceof Double) {
                        i = myCPGen.addDouble((double) lastValue);
                        instList.insert(handle, new LDC2_W(i));
                    }
                    if (lastValue instanceof Long) {
                        i = myCPGen.addLong((Long) lastValue);
                        instList.insert(handle, new LDC2_W(i));
					}

                }
			} 
			else if (handle.getInstruction() instanceof StoreInstruction) 
			{
                Number lastValue = getLastPush(instList, handle);
                if (canStore(instList, handle, lastValue)) {
                    InstructionHandle toDelete = handle;
                    handle = handle.getNext();
                    instDel(instList, toDelete);
                    instList.setPositions();
                } 
				else 
				{
                    handle = handle.getNext();
                }
            } 
			else 
			{
				handle = handle.getNext();
			}
			instList.setPositions();
		}
		
		//Possibly delete
		instList.setPositions(true);
        methGen.setMaxStack();
        methGen.setMaxLocals();
        Method newMethod = methGen.getMethod();
        Code newMethodCode = newMethod.getCode();
        InstructionList newInstList = new InstructionList(newMethodCode.getCode());
        cgen.replaceMethod(method, newMethod);
	}

	
	private Number getLastPush (InstructionList instList, InstructionHandle handle)
	{
		InstructionHandle lastOp = handle;
		do {lastOp = lastOp.getPrev(); } 
		while (!(stackChangingOp(lastOp) || lastOp != null));
		
		if (lastOp.getInstruction() instanceof IADD) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] + (int) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof IMUL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] * (int) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof ISUB) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] - (int) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof IDIV) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] / (int) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof INEG) 
		{
			Number number = getLastPush(instList, lastOp);
            if (number == null) return null;
			
            instDel(instList, lastOp);
            return (int)(0 - (int) number);
		}
		else if (lastOp.getInstruction() instanceof IREM) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] % (int) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof ISHL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] << (int) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof ISHR) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] >> (int) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof IAND) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] & (int) numbers[1]);
		}

		else if (lastOp.getInstruction() instanceof IOR) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[0] | (int) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof LADD) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] + (long) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof LMUL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] * (long) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof LSUB) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] - (long) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof LDIV) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] / (long) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof LNEG) 
		{
			Number number = getLastPush(instList, lastOp);
            if (number == null) return null;
			
            instDel(instList, lastOp);
            return (long)(0 - (long) number);
		}
		else if (lastOp.getInstruction() instanceof LREM) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] % (long) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof LSHL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] << (long) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof LSHR) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] >> (long) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof LAND) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] & (long) numbers[1]);
		}

		else if (lastOp.getInstruction() instanceof LOR) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[0] | (long) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof DADD) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[0] + (double) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof DMUL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[0] * (double) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof DSUB) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[0] - (double) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof DDIV) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[0] / (double) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof DNEG) 
		{
			Number number = getLastPush(instList, lastOp);
            if (number == null) return null;
			
            instDel(instList, lastOp);
            return (double)(0 - (double) number);
		}
		else if (lastOp.getInstruction() instanceof DREM) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[0] % (double) numbers[1]);
		} else if (lastOp.getInstruction() instanceof FADD) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[0] + (float) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof FMUL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[0] * (float) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof FSUB) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[0] - (float) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof FDIV) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[0] / (float) numbers[1]);
		}
		else if (lastOp.getInstruction() instanceof FNEG) 
		{
			Number number = getLastPush(instList, lastOp);
            if (number == null) return null;
			
            instDel(instList, lastOp);
            return (float)(0 - (float) number);
		}
		else if (lastOp.getInstruction() instanceof FREM) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[0] % (float) numbers[1]);
		}

		else if (lastOp.getInstruction() instanceof LDC) 
		{
			LDC instruction = (LDC) lastOp.getInstruction();
			Number num = (Number) instruction.getValue(myCPGen);
			instDel(instList,lastOp);
			return num;
		}
		return null;
	}
	
	private Boolean stackChangingOp(InstructionHandle handle) 
	{
		if (handle.getInstruction() instanceof ArithmeticInstruction ||
            handle.getInstruction() instanceof LocalVariableInstruction || 
			handle.getInstruction() instanceof StackInstruction) 
		{ 
            return true;
        }
        return false;
    }

	private Number[] lastValues(InstructionList instList, InstructionHandle handle)
	{
		Number[] numbers = new Number[2];
		numbers[0] = getLastPush(instList,handle);
		numbers[1] = getLastPush(instList,handle);
		
		if (numbers[0] == null || numbers[1] == null) return null;
		
		instDel(instList,handle);
		
		return numbers;
	
	}
	
	private void instDel(InstructionList instList, InstructionHandle handle) {
        instList.redirectBranches(handle, handle.getPrev());
        try {
            instList.delete(handle);
        } catch (Exception e) {
            // do nothing
        }
    }
	
	private boolean canStore(InstructionList instList, InstructionHandle handle, Number lastValue)
	{
		if (lastValue == null) return false;
		if (handle.getInstruction() instanceof ISTORE)
		{
			
		}
		else if (handle.getInstruction() instanceof ISTORE)
		{ 
		
		}
		else if (handle.getInstruction() instanceof ISTORE)
		{
		
		}
		else if (handle.getInstruction() instanceof ISTORE)
		{
		
		}

		return true;
	}
	
	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}