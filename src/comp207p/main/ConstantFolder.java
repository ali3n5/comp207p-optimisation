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
			else if (handle.getInstruction() instanceof NOP)
            {
				InstructionHandle remove = handle;
				handle = handle.getNext();
				instDel(instList, remove);
				instList.setPositions();
			}
            else if (handle.getInstruction() instanceof IINC)
            {
                int incVal = ((IINC) handle.getInstruction()).getIncrement();
                int index = ((IINC) handle.getInstruction()).getIndex();
                instList.insert(handle, new BIPUSH((byte) incVal));
                InstructionHandle incBp = handle.getPrev();
                instList.insert(handle, new ILOAD(index));
                instList.insert(handle, new IADD());
                instList.insert(handle, new ISTORE(index));
                try {
                    instList.redirectBranches(handle, incBp);
                    instList.delete(handle);
                } catch (Exception e) {
                    // do nothing
                }
                instList.setPositions();
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
//        boolean typeSwitch;
//        if (lastOp.getInstruction() instanceof typeSwitch) {
//            switch (typeSwitch) {
//                case BIPUSH:
//                    typeSwitch = BIPUSH;
//                    break;
//                case SIPUSH:
//                    typeSwitch = SIPUSH;
//                    break;
//                case ICONST:
//                    typeSwitch = ICONST;
//                    break;
//                case DCONST:
//                    typeSwitch = DCONST;
//                    break;
//                case FCONST:
//                    typeSwitch = FCONST;
//                    break;
//                case LCONST:
//                    typeSwitch = LCONST;
//                    break;
//            }
//            Number value = ((typeSwitch) lastOp.getInstruction()).getValue();
//            instDel(instList, lastOp);
//            return value;
//        }
        if (lastOp.getInstruction() instanceof BIPUSH) {
            Number value = ((BIPUSH) lastOp.getInstruction()).getValue();
            instDel(instList, lastOp);
            return value;
        }
        else if (lastOp.getInstruction() instanceof SIPUSH) {
            Number value = ((SIPUSH) lastOp.getInstruction()).getValue();
            instDel(instList, lastOp);
            return value;
        }
        else if (lastOp.getInstruction() instanceof ICONST) {
            Number value = ((ICONST) lastOp.getInstruction()).getValue();
            instDel(instList, lastOp);
            return value;
        }
        else if (lastOp.getInstruction() instanceof DCONST) {
            Number value = ((DCONST) lastOp.getInstruction()).getValue();
            instDel(instList, lastOp);
            return value;
        }
        else if (lastOp.getInstruction() instanceof FCONST) {
            Number value = ((FCONST) lastOp.getInstruction()).getValue();
            instDel(instList, lastOp);
            return value;
        }
        else if (lastOp.getInstruction() instanceof LCONST) {
            Number value = ((LCONST) lastOp.getInstruction()).getValue();
            instDel(instList, lastOp);
            return value;
        }
        else if (lastOp.getInstruction() instanceof IADD)
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
        else if (lastOp.getInstruction() instanceof ConversionInstruction) {
            if (lastOp.getInstruction() instanceof I2C) {
                return null;
            }
            Number firstNumber = getLastPush(instList, lastOp);
            if (firstNumber == null) {
                return null;
            }
            Number convertedNum = changeNum(lastOp, firstNumber);
            instDel(instList, lastOp);
            return convertedNum;
        }
		return null;
	}

    private Number changeNum(InstructionHandle lastOp, Number firstNumber) {

        if (lastOp.getInstruction() instanceof I2D) {
            return (double)((int) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof D2F) {
            return (float)((double) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof D2I) {
            return (int)((double) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof D2L) {
            return (long)((double) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof F2D) {
            return (double)((float) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof F2I) {
            return (int)((float) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof F2L) {
            return (long)((float) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof I2B) {
            return (byte)((int) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof I2D) {
            return (double)((int) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof I2F) {
            return (float)((int) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof I2L) {
            return (long)((int) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof I2S) {
            return (short)((int) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof L2D) {
            return (double)((long) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof L2F) {
            return (float)((long) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof L2I) {
            return (int)((long) firstNumber);
        }
        else if (lastOp.getInstruction() instanceof L2F) {
            return (float)((long) firstNumber);
        }
        return null;
    }

	private Boolean stackChangingOp(InstructionHandle handle)
	{
		if (handle.getInstruction() instanceof ArithmeticInstruction ||
            handle.getInstruction() instanceof LocalVariableInstruction ||
			handle.getInstruction() instanceof StackInstruction ||
            handle.getInstruction() instanceof BIPUSH ||
            handle.getInstruction() instanceof SIPUSH || handle.getInstruction() instanceof LCONST ||
            handle.getInstruction() instanceof DCONST || handle.getInstruction() instanceof FCONST ||
            handle.getInstruction() instanceof ICONST || handle.getInstruction() instanceof DCMPG ||
            handle.getInstruction() instanceof DCMPL || handle.getInstruction() instanceof FCMPG ||
            handle.getInstruction() instanceof FCMPL || handle.getInstruction() instanceof LCMP)
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

		if (handle.getInstruction() instanceof ISTORE) {
            int val = (int) lastValue;
            int storeIdx = ((ISTORE) handle.getInstruction()).getIndex();
            InstructionHandle handleNow = handle.getNext();
            int constantIdx = 0;

            if (val > 32767 || val < -32678) {
                constantIdx = myCPGen.addInteger((int) lastValue);
                // put sth in the constant pool
            }

            while (!(handleNow.getInstruction() instanceof ISTORE && ((ISTORE) handle.getInstruction()).getIndex() == storeIdx && handle.getInstruction().getOpcode() == handleNow.getInstruction().getOpcode())) {

                if (handleNow.getInstruction() instanceof ILOAD && ((ILOAD) handleNow.getInstruction()).getIndex() == storeIdx) {

                    if (val > 32767 || val < -32768) {
                        instList.insert(handleNow, new LDC(constantIdx));
                        instList.setPositions();
                        // insert the ldc we defined a few lines above
                    } else if (val < -128 || val > 127) {
                        instList.insert(handleNow, new SIPUSH((short) val));
                        instList.setPositions();
                    } else {
                        instList.insert(handleNow, new BIPUSH((byte) val));
                        instList.setPositions();
                    }

                    try {
                        handleNow = handleNow.getNext();
                        InstructionHandle handleDelete = handleNow.getPrev();
                        instList.redirectBranches(handleDelete, handleDelete.getPrev());
                        instList.delete(handleDelete);
                        instList.setPositions();
                    } catch (Exception e) {
                        //do nothing
                    }
                } else {
                    handleNow = handleNow.getNext();
                }
            }
            return true;
        }
		else if (handle.getInstruction() instanceof FSTORE)
		{
            float val = (float) lastValue;
            int storeIdx = ((FSTORE) handle.getInstruction()).getIndex();
            InstructionHandle handleNow = handle.getNext();
            int constantIdx = 0;
            constantIdx = myCPGen.addFloat((float) val);

            while (!(handleNow.getInstruction() instanceof FSTORE && ((FSTORE) handle.getInstruction()).getIndex() == storeIdx && handle.getInstruction().getOpcode() == handleNow.getInstruction().getOpcode())) {

                if (handleNow.getInstruction() instanceof FLOAD && ((FLOAD) handleNow.getInstruction()).getIndex() == storeIdx) {

                    instList.insert(handleNow, new LDC(constantIdx));
                    instList.setPositions();

                    try {
                        handleNow = handleNow.getNext();
                        InstructionHandle handleDelete = handleNow.getPrev();
                        instList.redirectBranches(handleDelete, handleDelete.getPrev());
                        instList.delete(handleDelete);
                        instList.setPositions();
                    } catch (Exception e) {
                        //do nothing
                    }
                } else {
                    handleNow = handleNow.getNext();
                }
            }
            return true;
		}
		else if (handle.getInstruction() instanceof DSTORE)
		{
            double val = (double) lastValue;
            int storeIdx = ((DSTORE) handle.getInstruction()).getIndex();
            InstructionHandle handleNow = handle.getNext();
            int constantIdx = 0;
            constantIdx = myCPGen.addDouble((double) val);

            while (!(handleNow.getInstruction() instanceof DSTORE && ((DSTORE) handle.getInstruction()).getIndex() == storeIdx && handle.getInstruction().getOpcode() == handleNow.getInstruction().getOpcode())) {

                if (handleNow.getInstruction() instanceof DLOAD && ((DLOAD) handleNow.getInstruction()).getIndex() == storeIdx) {
                    instList.insert(handleNow, new LDC2_W(constantIdx));
                    instList.setPositions();

                    try {
                        handleNow = handleNow.getNext();
                        InstructionHandle handleDelete = handleNow.getPrev();
                        instList.redirectBranches(handleDelete, handleDelete.getPrev());
                        instList.delete(handleDelete);
                        instList.setPositions();
                    } catch (Exception e) {
                        //do nothing
                    }
                } else {
                    handleNow = handleNow.getNext();
                }
            }
            return true;
		}
		else if (handle.getInstruction() instanceof LSTORE)
		{
            long val = (long) lastValue;
            int storeIdx = ((LSTORE) handle.getInstruction()).getIndex();
            InstructionHandle handleNow = handle.getNext();
            int constantIdx = 0;
            constantIdx = myCPGen.addLong((long) val);

            while (handleNow != null && !(handleNow.getInstruction() instanceof LSTORE && ((LSTORE) handle.getInstruction()).getIndex() == storeIdx && handle.getInstruction().getOpcode() == handleNow.getInstruction().getOpcode())) {

                if (handleNow.getInstruction() instanceof LLOAD && ((LLOAD) handleNow.getInstruction()).getIndex() == storeIdx) {

                    instList.insert(handleNow, new LDC2_W(constantIdx));
                    instList.setPositions();

                    try {
                        handleNow = handleNow.getNext();
                        InstructionHandle handleDelete = handleNow.getPrev();
                        instList.redirectBranches(handleDelete, handleDelete.getPrev());
                        instList.delete(handleDelete);
                        instList.setPositions();
                    } catch (Exception e) {
                        //do nothing
                    }
                } else {
                    handleNow = handleNow.getNext();
                }
            }
            return true;
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