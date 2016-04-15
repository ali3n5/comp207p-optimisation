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

	ConstantPoolGen CPGen = null;

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
		this.CPGen = cpgen;
		
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
                        i = CPGen.addInteger((int) lastValue);
                        instList.insert(handle, new LDC(i));
                    }
                    if (lastValue instanceof Float) {
                        i = CPGen.addFloat((float) lastValue);
                        instList.insert(handle, new LDC(i));
                    }
                    if (lastValue instanceof Double) {
                        i = CPGen.addDouble((double) lastValue);
                        instList.insert(handle, new LDC2_W(i));
                    }
                    if (lastValue instanceof Long) {
                        i = CPGen.addLong((Long) lastValue);
                        instList.insert(handle, new LDC2_W(i));
					}

                }
			} 
			else if (handle.getInstruction() instanceof StoreInstruction) 
			{
                Number lastValue = getLastPush(instList, handle);
                if (canStore(instList, handle, lastValue)) 
				{
					handleStore(instList, handle, lastValue);
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
//            else if (handle.getInstruction() instanceof IfInstruction)
////                focus on ints for dynamic variable constant folding optimisation
//            {
//                Number lastValue = getLastPush(instList, handle);
//                if(compInt(instList, handle, lastValue))
//                {
//                    handleIf(instList, handle, lastValue);
//                    InstructionHandle remove = handle;
//                    handle = handle.getNext();
//                    instDel(instList, remove);
//                    instList.setPositions();
//                }
//            }
			else if (handle.getInstruction() instanceof NOP)
			{
				InstructionHandle remove = handle;
				handle = handle.getNext();
				instDel(instList, remove);
				instList.setPositions();
			}
			else 
			{
				handle = handle.getNext();
			}
			instList.setPositions();
		}

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
			
			return ((int) numbers[1] - (int) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof IDIV) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[1] / (int) numbers[0]);
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
			
			return ((int) numbers[1] % (int) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof ISHL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[1] << (int) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof ISHR) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[1] >> (int) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof IAND) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[1] & (int) numbers[0]);
		}

		else if (lastOp.getInstruction() instanceof IOR) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((int) numbers[1] | (int) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof LADD) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[1] + (long) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof LMUL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[1] * (long) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof LSUB) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[1] - (long) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof LDIV) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[1] / (long) numbers[0]);
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
			
			return ((long) numbers[1] % (long) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof LSHL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[1] << (long) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof LSHR) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[1] >> (long) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof LAND) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[1] & (long) numbers[0]);
		}

		else if (lastOp.getInstruction() instanceof LOR) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((long) numbers[1] | (long) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof DADD) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[1] + (double) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof DMUL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[1] * (double) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof DSUB) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[1] - (double) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof DDIV) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((double) numbers[1] / (double) numbers[0]);
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
			
			return ((double) numbers[1] % (double) numbers[0]);
		} else if (lastOp.getInstruction() instanceof FADD) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[1] + (float) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof FMUL) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[1] * (float) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof FSUB) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[1] - (float) numbers[0]);
		}
		else if (lastOp.getInstruction() instanceof FDIV) 
		{
			Number[] numbers = lastValues(instList,lastOp);
			if (numbers == null) return null;
			
			return ((float) numbers[1] / (float) numbers[0]);
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
			
			return ((float) numbers[1] % (float) numbers[0]);
		}
		
		else if (lastOp.getInstruction() instanceof BIPUSH)
		{
			Number num = ((BIPUSH) lastOp.getInstruction()).getValue();
			instDel(instList,lastOp);
			return num;
		}
		else if (lastOp.getInstruction() instanceof SIPUSH)
		{
			Number num = ((SIPUSH) lastOp.getInstruction()).getValue();
			instDel(instList,lastOp);
			return num;
		}
		else if (lastOp.getInstruction() instanceof ICONST)
		{
			Number num = ((ICONST) lastOp.getInstruction()).getValue();
			instDel(instList,lastOp);
			return num;
		}
		else if (lastOp.getInstruction() instanceof DCONST)
		{
			Number num = ((DCONST) lastOp.getInstruction()).getValue();
			instDel(instList,lastOp);
			return num;
		}
		else if (lastOp.getInstruction() instanceof FCONST)
		{
			Number num = ((FCONST) lastOp.getInstruction()).getValue();
			instDel(instList,lastOp);
			return num;
		}
		else if (lastOp.getInstruction() instanceof LCONST)
		{
			Number num = ((LCONST) lastOp.getInstruction()).getValue();
			instDel(instList,lastOp);
			return num;
		}
        else if (lastOp.getInstruction() instanceof IUSHR)
        {
            Number[] num = lastValues(instList, lastOp);
            if (num == null) {return null;}
            return ((int) num[1] >>> (int) num[0]);
        }
        else if (lastOp.getInstruction() instanceof IXOR)
        {
            Number[] num = lastValues(instList, lastOp);
            if (num == null) {return null;}
            return ((int) num[1] ^ (int) num[0]);
        }
        else if (lastOp.getInstruction() instanceof LUSHR)
        {
            Number[] num = lastValues(instList, lastOp);
            if (num == null) {return null;}
            return ((long) num[1] >>> (long) num[0]);
        }
        else if (lastOp.getInstruction() instanceof LXOR)
        {
            Number[] num = lastValues(instList, lastOp);
            if (num == null) {return null;}
            return ((long) num[1] ^ (long) num[0]);
        }
        else if (lastOp.getInstruction() instanceof DCMPG)
        {
            Number[] number = lastValues(instList, lastOp);
            if (number == null) {return null;}
            if ((double) number[1] == (double) number[0]) {return 0;}
            else if ((double) number[1] > (double) number[0]) {return 1;}
            else {return -1;}
        }
        else if (lastOp.getInstruction() instanceof DCMPL)
        {
            Number[] number = lastValues(instList, lastOp);
            if (number == null) {return null;}
            if ((double) number[1] == (double) number[0]) {return 0;}
            else if ((double) number[1] < (double) number[0]) {return 1;}
            else {return -1;}
        }
        else if (lastOp.getInstruction() instanceof LCMP)
        {
            Number[] number = lastValues(instList, lastOp);
            if (number == null) {return null;}
            if ((double) number[1] == (double) number[0]) {return 0;}
            else if ((double) number[1] > (double) number[0]) {return 1;}
            else {return -1;}
        }
        else if (lastOp.getInstruction() instanceof FCMPG)
        {
            Number[] number = lastValues(instList, lastOp);
            if (number == null) {return null;}
            if ((float) number[1] == (float) number[0]) {return 0;}
            else if ((float) number[1] > (float) number[0]) {return 1;}
            else {return -1;}
        }
        else if (lastOp.getInstruction() instanceof FCMPL) {
            Number[] number = lastValues(instList, lastOp);
            if (number == null) {
                return null;
            }
            if ((float) number[1] == (float) number[0]) {return 0;}
            else if ((float) number[1] < (float) number[0]) {return 1;} else {return -1;}
        }
		else if (lastOp.getInstruction() instanceof ConversionInstruction)
		{
			Number num = getLastPush(instList,lastOp);
			if (num == null) return null;
			Number convNum = convert(lastOp,num);
			instDel(instList,lastOp);
			return convNum;
		}
		else if (lastOp.getInstruction() instanceof LDC) 
		{
			LDC instruction = (LDC) lastOp.getInstruction();
			Number num = (Number) instruction.getValue(CPGen);
			instDel(instList,lastOp);
			return num;
		}
		else if (lastOp.getInstruction() instanceof LDC2_W) 
		{
			LDC2_W instruction = (LDC2_W) lastOp.getInstruction();
			Number num = (Number) instruction.getValue(CPGen);
			instDel(instList,lastOp);
			return num;
		}
		return null;
	}
	
	private Boolean stackChangingOp(InstructionHandle handle) 
	{
		if (handle.getInstruction() instanceof ArithmeticInstruction ||
            handle.getInstruction() instanceof LocalVariableInstruction || 
			handle.getInstruction() instanceof StackInstruction ||
             handle.getInstruction() instanceof BranchInstruction ||
			handle.getInstruction() instanceof BIPUSH ||
            handle.getInstruction() instanceof SIPUSH || 
			handle.getInstruction() instanceof LCONST ||
            handle.getInstruction() instanceof DCONST || 
			handle.getInstruction() instanceof FCONST ||
            handle.getInstruction() instanceof ICONST ||
            handle.getInstruction() instanceof DCMPG ||
            handle.getInstruction() instanceof DCMPL ||
            handle.getInstruction() instanceof FCMPG ||
            handle.getInstruction() instanceof FCMPL ||
            handle.getInstruction() instanceof LCMP)
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
        try 
		{
            instList.delete(handle);
        } 
		catch (Exception e) 
		{	}
    }
	
	private Number convert (InstructionHandle lastOp, Number num)
	{
		if (lastOp.getInstruction() instanceof I2B)
			return (byte)((int) num);
		else if (lastOp.getInstruction() instanceof I2D)
			return (double)((int) num); 
		else if (lastOp.getInstruction() instanceof I2F)
			return (float)((int) num); 
		else if (lastOp.getInstruction() instanceof I2L)
			return (long)((int) num); 
		else if (lastOp.getInstruction() instanceof D2F)
			return (float)((double) num); 
		else if (lastOp.getInstruction() instanceof D2I)
			return (int)((double) num); 
		else if (lastOp.getInstruction() instanceof D2L)
			return (long)((double) num); 
		else if (lastOp.getInstruction() instanceof L2D)
			return (double)((long) num); 
		else if (lastOp.getInstruction() instanceof L2F)
			return (float)((long) num); 
		else if (lastOp.getInstruction() instanceof L2I)
			return (int)((long) num); 
		else if (lastOp.getInstruction() instanceof F2I)
			return (int)((float) num); 
		else if (lastOp.getInstruction() instanceof F2D)
			return (double)((float) num); 
		else if (lastOp.getInstruction() instanceof F2L)
			return (long)((float) num); 
		else
			return null;
	}
	
	private boolean canStore(InstructionList instList, InstructionHandle originalHandle, Number lastValue) 
	{
		if (lastValue == null)
			return false;
		else if (originalHandle.getInstruction() instanceof ISTORE || originalHandle.getInstruction() instanceof FSTORE || originalHandle.getInstruction() instanceof LSTORE || originalHandle.getInstruction() instanceof DSTORE)
			return true;
		else
			return false;
	}
	private void handleStore(InstructionList instList, InstructionHandle originalHandle, Number lastValue) 
	{
		if (lastValue == null)
			return;
        if (originalHandle.getInstruction() instanceof ISTORE) {
            int num = (int) lastValue;
            int index = ((ISTORE) originalHandle.getInstruction()).getIndex();
            InstructionHandle handle = originalHandle.getNext();
            int constIndex = CPGen.addInteger((int) num);
            while (handle != null && !(handle.getInstruction() instanceof ISTORE && ((ISTORE) originalHandle.getInstruction()).getIndex() == index && originalHandle.getInstruction().getOpcode() == handle.getInstruction().getOpcode())) {

                if (handle.getInstruction() instanceof ILOAD && ((ILOAD) handle.getInstruction()).getIndex() == index) {

                    instList.insert(handle, new LDC(constIndex));
                    instList.setPositions();
					
					InstructionHandle toDelete = handle;
                    handle = handle.getNext();
                    
					instDel(instList,toDelete);
                    instList.setPositions();
                  
                } else {
                    handle = handle.getNext();
                }
            }
            return;		
        } else if (originalHandle.getInstruction() instanceof FSTORE) {
            float num = (float) lastValue;
            int index = ((FSTORE) originalHandle.getInstruction()).getIndex();
            InstructionHandle handle = originalHandle.getNext();
            int constIndex = CPGen.addFloat((float) num);
            while (handle != null && !(handle.getInstruction() instanceof FSTORE && ((FSTORE) originalHandle.getInstruction()).getIndex() == index && originalHandle.getInstruction().getOpcode() == handle.getInstruction().getOpcode())) {

                if (handle.getInstruction() instanceof FLOAD && ((FLOAD) handle.getInstruction()).getIndex() == index) {

                    instList.insert(handle, new LDC(constIndex));
                    instList.setPositions();
					
					InstructionHandle toDelete = handle;
                    handle = handle.getNext();
                    
					instDel(instList,toDelete);
                    instList.setPositions();
                  
                } else {
                    handle = handle.getNext();
                }
            }
            return;
        } else if (originalHandle.getInstruction() instanceof DSTORE)  {
            double num = (double) lastValue;
            int index = ((DSTORE) originalHandle.getInstruction()).getIndex();
            InstructionHandle handle = originalHandle.getNext();
            int constIndex = CPGen.addDouble((double) num);
            while (handle != null && !(handle.getInstruction() instanceof DSTORE && ((DSTORE) originalHandle.getInstruction()).getIndex() == index && originalHandle.getInstruction().getOpcode() == handle.getInstruction().getOpcode())) {

                if (handle.getInstruction() instanceof DLOAD && ((DLOAD) handle.getInstruction()).getIndex() == index) {

                    instList.insert(handle, new LDC2_W(constIndex));
                    instList.setPositions();
					
					InstructionHandle toDelete = handle;
                    handle = handle.getNext();
                    
					instDel(instList,toDelete);
                    instList.setPositions();
                  
                } else {
                    handle = handle.getNext();
                }
            }
            return;
        } else if (originalHandle.getInstruction() instanceof LSTORE) {
            long num = (long) lastValue;
            int index = ((LSTORE) originalHandle.getInstruction()).getIndex();
            InstructionHandle handle = originalHandle.getNext();
            int constIndex = CPGen.addLong((long) num);
            while (handle != null && !(handle.getInstruction() instanceof LSTORE && ((LSTORE) originalHandle.getInstruction()).getIndex() == index && originalHandle.getInstruction().getOpcode() == handle.getInstruction().getOpcode())) {

                if (handle.getInstruction() instanceof LLOAD && ((LLOAD) handle.getInstruction()).getIndex() == index) {

                    instList.insert(handle, new LDC2_W(constIndex));
                    instList.setPositions();
					
					InstructionHandle toDelete = handle;
                    handle = handle.getNext();
                    
					instDel(instList,toDelete);
                    instList.setPositions();
                  
                } else {
                    handle = handle.getNext();
                }
            }
            return;
        }
        return;
    }

    private boolean compInt(InstructionList instList, InstructionHandle originalHandle, Number lastValue)
    {
        if (lastValue == null)
            return false;
        else if (originalHandle.getInstruction() instanceof IF_ICMPEQ
                || originalHandle.getInstruction() instanceof IFGE
                || originalHandle.getInstruction() instanceof IF_ICMPGT
                || originalHandle.getInstruction() instanceof IF_ICMPLE
                || originalHandle.getInstruction() instanceof IF_ICMPLT)
            // ignore negation
            return true;
        else
            return false;
    }

    private void handleIf(InstructionList instList, InstructionHandle originalHandle, Number lastValue)
    {
        if (lastValue == null)
        {
            return;
        }
        int num = (int) lastValue;
        if(originalHandle.getInstruction() instanceof IFGE)
        {
//            do something about it
            int index = ((IFGE) originalHandle.getInstruction()).getIndex();
            InstructionHandle handle = originalHandle.getNext();
            int constIndex = CPGen.addInteger((int) num);
            while (handle != null && !(handle.getInstruction() instanceof IFGE && ((IFGE) originalHandle.getInstruction()).getIndex() == index && originalHandle.getInstruction().getOpcode() == handle.getInstruction().getOpcode())) {

                if (handle.getInstruction() instanceof IFGE && ((IFGE) handle.getInstruction()).getIndex() == index) {

                    instList.insert(handle, new LDC(constIndex));
                    instList.setPositions();

                    InstructionHandle toDelete = handle;
                    handle = handle.getNext();

                    instDel(instList,toDelete);
                    instList.setPositions();

                } else {
                    handle = handle.getNext();
                }
            }
            return;
        }
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