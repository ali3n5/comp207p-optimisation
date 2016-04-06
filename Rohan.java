/ COMP2010 Coursework 2 - Folding Optimisation
// Sam Fallahi, Rohan Kopparapu, David Lipowicz

package comp2010.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantInteger;

import org.apache.bcel.generic.*;

public class ConstantFolder {

    ClassParser parser = null;
    ClassGen gen = null;

    JavaClass original = null;
    JavaClass optimized = null;

    ConstantPoolGen myCPGen = null;

    public ConstantFolder(String classFilePath) {
        try {
            this.parser = new ClassParser(classFilePath);
            this.original = this.parser.parse();
            this.gen = new ClassGen(this.original);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method) {
        this.myCPGen = cpgen;

        // Get the Code of the method, which is a collection of bytecode instructions
        Code methodCode = method.getCode();

        // Now get the actualy bytecode data in byte array, 
        // and use it to initialise an InstructionList
        InstructionList instList = new InstructionList(methodCode.getCode());

        // Initialise a method generator with the original method as the baseline   
        MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(), null, method.getName(), cgen.getClassName(), instList, cpgen);

        ConstantPool cp = cpgen.getConstantPool();
        // get the constants in the pool
        Constant[] constants = cp.getConstantPool();

        InstructionHandle valueHolder;

        // InstructionHandle is a wrapper for actual Instructions
        for (InstructionHandle handle = instList.getStart(); handle != null;) {

            if (handle.getInstruction() instanceof StoreInstruction) {

                Number lastStackValue = getLastStackPush(instList, handle);
                if (handleStoreInstructions(instList, handle, lastStackValue)) {
                    InstructionHandle handleDelete = handle;
                    handle = handle.getNext();
                    safeInstructionDelete(instList, handleDelete);
                    instList.setPositions();
                } else {
                    handle = handle.getNext();
                }
            } else if (handle.getInstruction() instanceof NOP) {
                InstructionHandle handleDelete = handle;
                handle = handle.getNext();
                safeInstructionDelete(instList, handleDelete);
                instList.setPositions();
            } else if (handle.getInstruction() instanceof ArithmeticInstruction) {

                InstructionHandle toHandle = handle.getNext();
                handle = handle.getNext();
                Number lastStackValue = getLastStackPush(instList, toHandle);

                if (lastStackValue != null) {

                    int constantIndex = 0;
                    if (lastStackValue instanceof Integer) {
                        constantIndex = myCPGen.addInteger((int) lastStackValue);
                        instList.insert(handle, new LDC(constantIndex));
                        instList.setPositions();
                    }
                    if (lastStackValue instanceof Float) {
                        constantIndex = myCPGen.addFloat((float) lastStackValue);
                        instList.insert(handle, new LDC(constantIndex));
                        instList.setPositions();
                    }
                    if (lastStackValue instanceof Double) {
                        constantIndex = myCPGen.addDouble((double) lastStackValue);
                        instList.insert(handle, new LDC2_W(constantIndex));
                        instList.setPositions();
                    }
                    if (lastStackValue instanceof Long) {
                        constantIndex = myCPGen.addLong((Long) lastStackValue);
                        instList.insert(handle, new LDC2_W(constantIndex));
                        instList.setPositions();
                    }
                }
            } else if (handle.getInstruction() instanceof IINC) {
                int incValue = ((IINC) handle.getInstruction()).getIncrement();
                int index = ((IINC) handle.getInstruction()).getIndex();
                instList.insert(handle, new BIPUSH((byte) incValue));
                InstructionHandle incBipush = handle.getPrev();
                instList.insert(handle, new ILOAD(index));
                instList.insert(handle, new IADD());
                instList.insert(handle, new ISTORE(index));
                try {
                    instList.redirectBranches(handle, incBipush);
                    instList.delete(handle);
                } catch (Exception e) {
                    // do nothing
                }
                instList.setPositions();
            }else {
                handle = handle.getNext();
                instList.setPositions();
            }
        }

        instList.setPositions(true);
        methodGen.setMaxStack();
        methodGen.setMaxLocals();
        Method newMethod = methodGen.getMethod();
        Code newMethodCode = newMethod.getCode();
        InstructionList newInstList = new InstructionList(newMethodCode.getCode());
        cgen.replaceMethod(method, newMethod);

    }

    // returns true if a computable number is being stored in the stack
    private boolean handleStoreInstructions(InstructionList instList, InstructionHandle handle, Number lastStackValue) {

        if (handle.getInstruction() instanceof ISTORE && lastStackValue != null) {
            int value = (int) lastStackValue;
            int istoreIndex = ((ISTORE) handle.getInstruction()).getIndex();
            InstructionHandle handleNow = handle.getNext();
            int constantIndex = 0;

            if (value > 32767 || value < -32678) {
                constantIndex = myCPGen.addInteger((int) value);
                // put sth in the constant pool 
            }

            while (handleNow != null && !(handleNow.getInstruction() instanceof ISTORE && ((ISTORE) handle.getInstruction()).getIndex() == istoreIndex && handle.getInstruction().getOpcode() == handleNow.getInstruction().getOpcode())) {

                if (handleNow.getInstruction() instanceof ILOAD && ((ILOAD) handleNow.getInstruction()).getIndex() == istoreIndex) {

                    if (value > 32767 || value < -32768) {
                        instList.insert(handleNow, new LDC(constantIndex));
                        instList.setPositions();
                        // insert the ldc we defined a few lines above
                    } else if (value < -128 || value > 127) {
                        instList.insert(handleNow, new SIPUSH((short) value));
                        instList.setPositions();
                    } else {
                        instList.insert(handleNow, new BIPUSH((byte) value));
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
        } else if (handle.getInstruction() instanceof FSTORE && lastStackValue != null) {
            float value = (float) lastStackValue;
            int istoreIndex = ((FSTORE) handle.getInstruction()).getIndex();
            InstructionHandle handleNow = handle.getNext();
            int constantIndex = 0;
            constantIndex = myCPGen.addFloat((float) value);
            while (handleNow != null && !(handleNow.getInstruction() instanceof FSTORE && ((FSTORE) handle.getInstruction()).getIndex() == istoreIndex && handle.getInstruction().getOpcode() == handleNow.getInstruction().getOpcode())) {

                if (handleNow.getInstruction() instanceof FLOAD && ((FLOAD) handleNow.getInstruction()).getIndex() == istoreIndex) {

                    instList.insert(handleNow, new LDC(constantIndex));
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
        } else if (handle.getInstruction() instanceof DSTORE && lastStackValue != null) {
            double value = (double) lastStackValue;
            int istoreIndex = ((DSTORE) handle.getInstruction()).getIndex();
            InstructionHandle handleNow = handle.getNext();
            int constantIndex = 0;
            constantIndex = myCPGen.addDouble((double) value);
            
            while (handleNow != null && !(handleNow.getInstruction() instanceof DSTORE && ((DSTORE) handle.getInstruction()).getIndex() == istoreIndex && handle.getInstruction().getOpcode() == handleNow.getInstruction().getOpcode())) {
                
                if (handleNow.getInstruction() instanceof DLOAD && ((DLOAD) handleNow.getInstruction()).getIndex() == istoreIndex) {
                    instList.insert(handleNow, new LDC2_W(constantIndex));
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
        } else if (handle.getInstruction() instanceof LSTORE && lastStackValue != null) {
            long value = (long) lastStackValue;
            int istoreIndex = ((LSTORE) handle.getInstruction()).getIndex();
            InstructionHandle handleNow = handle.getNext();
            int constantIndex = 0;
            constantIndex = myCPGen.addLong((long) value);

            while (handleNow != null && !(handleNow.getInstruction() instanceof LSTORE && ((LSTORE) handle.getInstruction()).getIndex() == istoreIndex && handle.getInstruction().getOpcode() == handleNow.getInstruction().getOpcode())) {

                if (handleNow.getInstruction() instanceof LLOAD && ((LLOAD) handleNow.getInstruction()).getIndex() == istoreIndex) {

                    instList.insert(handleNow, new LDC2_W(constantIndex));
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
        return false;
    }

    private Number getLastStackPush(InstructionList instList, InstructionHandle handle) {
        InstructionHandle lastStackOp = handle;
        do {
            lastStackOp = lastStackOp.getPrev();
        } while (!(stackChangingOp(lastStackOp) || lastStackOp != null));

        if (lastStackOp.getInstruction() instanceof BIPUSH) {
            Number value = ((BIPUSH) lastStackOp.getInstruction()).getValue();
            safeInstructionDelete(instList, lastStackOp);
            return value;
        } else if (lastStackOp.getInstruction() instanceof SIPUSH) {
            Number value = ((SIPUSH) lastStackOp.getInstruction()).getValue();
            safeInstructionDelete(instList, lastStackOp);
            return value;
        } else if (lastStackOp.getInstruction() instanceof ICONST) {
            Number value = ((ICONST) lastStackOp.getInstruction()).getValue();
            safeInstructionDelete(instList, lastStackOp);
            return value;
        } else if (lastStackOp.getInstruction() instanceof DCONST) {
            Number value = ((DCONST) lastStackOp.getInstruction()).getValue();
            safeInstructionDelete(instList, lastStackOp);
            return value;
        } else if (lastStackOp.getInstruction() instanceof FCONST) {
            Number value = ((FCONST) lastStackOp.getInstruction()).getValue();
            safeInstructionDelete(instList, lastStackOp);
            return value;
        } else if (lastStackOp.getInstruction() instanceof LCONST) {
            Number value = ((LCONST) lastStackOp.getInstruction()).getValue();
            safeInstructionDelete(instList, lastStackOp);
            return value;
        } else if (lastStackOp.getInstruction() instanceof IADD) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[0] + (int) number[1]);
        } else if (lastStackOp.getInstruction() instanceof IMUL) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[0] * (int) number[1]);
        } else if (lastStackOp.getInstruction() instanceof IDIV) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[1] / (int) number[0]);
        } else if (lastStackOp.getInstruction() instanceof ISUB) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[1] - (int) number[0]);
        } else if (lastStackOp.getInstruction() instanceof IREM) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[1] % (int) number[0]);
        } else if (lastStackOp.getInstruction() instanceof IAND) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[1] & (int) number[0]);
        } else if (lastStackOp.getInstruction() instanceof INEG) {
            Number firstNumber = getLastStackPush(instList, lastStackOp);
            if (firstNumber == null) {
                return null;
            }
            safeInstructionDelete(instList, lastStackOp);
            return (int)(0 - (int) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof IOR) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[1] | (int) number[0]);
        } else if (lastStackOp.getInstruction() instanceof ISHL) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[1] << (int) number[0]);
        } else if (lastStackOp.getInstruction() instanceof ISHR) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[0] >> (int) number[1]);
        } else if (lastStackOp.getInstruction() instanceof IUSHR) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[1] >>> (int) number[0]);
        } else if (lastStackOp.getInstruction() instanceof IXOR) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((int) number[1] ^ (int) number[0]);
        } else if (lastStackOp.getInstruction() instanceof LADD) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[0] + (long) number[1]);
        } else if (lastStackOp.getInstruction() instanceof LMUL) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[0] * (long) number[1]);
        } else if (lastStackOp.getInstruction() instanceof LDIV) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[1] / (long) number[0]);
        } else if (lastStackOp.getInstruction() instanceof LSUB) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[1] - (long) number[0]);
        } else if (lastStackOp.getInstruction() instanceof LREM) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[1] % (long) number[0]);
        } else if (lastStackOp.getInstruction() instanceof LNEG) {
            Number firstNumber = getLastStackPush(instList, lastStackOp);
            if (firstNumber == null) {
                return null;
            }
            safeInstructionDelete(instList, lastStackOp);
            return (long)(0 - (long) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof LOR) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[1] | (long) number[0]);
        } else if (lastStackOp.getInstruction() instanceof LSHL) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[1] << (long) number[0]);
        } else if (lastStackOp.getInstruction() instanceof LSHR) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[1] >> (long) number[0]);
        } else if (lastStackOp.getInstruction() instanceof LUSHR) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[1] >>> (long) number[0]);
        } else if (lastStackOp.getInstruction() instanceof LXOR) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((long) number[1] ^ (long) number[0]);
        } else if (lastStackOp.getInstruction() instanceof FADD) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((float) number[0] + (float) number[1]);
        } else if (lastStackOp.getInstruction() instanceof FMUL) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((float) number[0] * (float) number[1]);
        } else if (lastStackOp.getInstruction() instanceof FDIV) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((float) number[1] / (float) number[0]);
        } else if (lastStackOp.getInstruction() instanceof FSUB) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((float) number[1] - (float) number[0]);
        } else if (lastStackOp.getInstruction() instanceof FREM) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((float) number[1] % (float) number[0]);
        } else if (lastStackOp.getInstruction() instanceof FNEG) {
            Number firstNumber = getLastStackPush(instList, lastStackOp);
            if (firstNumber == null) {
                return null;
            }
            safeInstructionDelete(instList, lastStackOp);
            return (float)(0 - (float) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof DADD) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((double) number[0] + (double) number[1]);
        } else if (lastStackOp.getInstruction() instanceof DMUL) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((double) number[0] * (double) number[1]);
        } else if (lastStackOp.getInstruction() instanceof DDIV) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((double) number[1] / (double) number[0]);
        } else if (lastStackOp.getInstruction() instanceof DSUB) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((double) number[1] - (double) number[0]);
        } else if (lastStackOp.getInstruction() instanceof DREM) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            return ((double) number[1] % (double) number[0]);
        } else if (lastStackOp.getInstruction() instanceof DCMPG) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            if ((double) number[1] == (double) number[0]) {
                return 0;
            } else if ((double) number[1] > (double) number[0]) {
                return 1;
            } else {
                return -1;
            }
        } else if (lastStackOp.getInstruction() instanceof DCMPL) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            if ((double) number[1] == (double) number[0]) {
                return 0;
            } else if ((double) number[1] < (double) number[0]) {
                return 1;
            } else {
                return -1;
            }
        } else if (lastStackOp.getInstruction() instanceof DNEG) {
            Number firstNumber = getLastStackPush(instList, lastStackOp);
            if (firstNumber == null) {
                return null;
            }
            safeInstructionDelete(instList, lastStackOp);
            return (double)(0 - (double) firstNumber);

       } else if (lastStackOp.getInstruction() instanceof LCMP) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            if ((double) number[1] == (double) number[0]) {
                return 0;
            } else if ((double) number[1] > (double) number[0]) {
                return 1;
            } else {
                return -1;
            }
        } else if (lastStackOp.getInstruction() instanceof FCMPG) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            if ((float) number[1] == (float) number[0]) {
                return 0;
            } else if ((float) number[1] > (float) number[0]) {
                return 1;
            } else {
                return -1;
            }
        } else if (lastStackOp.getInstruction() instanceof FCMPL) {
            Number[] number = binaryOps(instList, lastStackOp);
            if (number == null) {
                return null;
            }
            if ((float) number[1] == (float) number[0]) {
                return 0;
            } else if ((float) number[1] < (float) number[0]) {
                return 1;
            } else {
                return -1;
            }
        } else if (lastStackOp.getInstruction() instanceof LDC) {
            // load the corresponding numba from the constant pool and return it :D
            LDC ldc = (LDC) lastStackOp.getInstruction();
            Number value = (Number) ldc.getValue(myCPGen);
            safeInstructionDelete(instList, lastStackOp);
            return value;
        } else if (lastStackOp.getInstruction() instanceof LDC2_W) {
            // load the corresponding numba from the constant pool and return it :D
            LDC2_W ldc2_w = (LDC2_W) lastStackOp.getInstruction();
            Number value = (Number) ldc2_w.getValue(myCPGen);
            safeInstructionDelete(instList, lastStackOp);
            return value;
        } else if (lastStackOp.getInstruction() instanceof ConversionInstruction){
            if (lastStackOp.getInstruction() instanceof I2C) { 
                return null;
            }
            Number firstNumber = getLastStackPush(instList, lastStackOp);
            if (firstNumber == null) {
                return null;
            }
            Number convertedNum = convertNumber(lastStackOp, firstNumber);
            safeInstructionDelete(instList, lastStackOp);
            return convertedNum;
        }
        return null;
    }

    private Number convertNumber(InstructionHandle lastStackOp, Number firstNumber) {

        if (lastStackOp.getInstruction() instanceof I2D) {
            return (double)((int) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof D2F) {
            return (float)((double) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof D2I) {
            return (int)((double) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof D2L) {
            return (long)((double) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof F2D) {
            return (double)((float) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof F2I) {
            return (int)((float) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof F2L) {
            return (long)((float) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof I2B) {
            return (byte)((int) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof I2D) {
            return (double)((int) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof I2F) {
            return (float)((int) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof I2L) {
            return (long)((int) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof I2S) {
            return (short)((int) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof L2D) {
            return (double)((long) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof L2F) {
            return (float)((long) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof L2I) {
            return (int)((long) firstNumber);
        } else if (lastStackOp.getInstruction() instanceof L2F) {
            return (float)((long) firstNumber);
        }
        return null;
    }

    private Boolean stackChangingOp(InstructionHandle handle) {
        if (handle.getInstruction() instanceof ArithmeticInstruction || handle.getInstruction() instanceof BIPUSH ||
              handle.getInstruction() instanceof SIPUSH || handle.getInstruction() instanceof LCONST ||
              handle.getInstruction() instanceof DCONST || handle.getInstruction() instanceof FCONST ||
              handle.getInstruction() instanceof ICONST || handle.getInstruction() instanceof DCMPG ||
              handle.getInstruction() instanceof DCMPL || handle.getInstruction() instanceof FCMPG ||
              handle.getInstruction() instanceof FCMPL || handle.getInstruction() instanceof LCMP ||
              handle.getInstruction() instanceof LocalVariableInstruction || handle.getInstruction() instanceof StackInstruction) {
            
            return true;
        }
        return false;
    }

    private void safeInstructionDelete(InstructionList instList, InstructionHandle nodeToDelete) {
        instList.redirectBranches(nodeToDelete, nodeToDelete.getPrev());
        try {
            instList.delete(nodeToDelete);
        } catch (Exception e) {
            // do nothing
        }
    }

    public void optimize() {
        // load the original class into a class generator
        ClassGen cgen = new ClassGen(original);
        ConstantPoolGen cpgen = cgen.getConstantPool();
        cgen.setMajor(50);
        // Do your optimization here
        Method[] methods = cgen.getMethods();
        for (Method m: methods) {
            optimizeMethod(cgen, cpgen, m);
        }

        // Do your optimization here
        this.optimized = cgen.getJavaClass();
    }

    public void write(String optimisedFilePath) {
        this.optimize();

        try {
            FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
            this.optimized.dump(out);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private Number[] binaryOps(InstructionList instList, InstructionHandle lastStackOp) {
        Number[] number = new Number[2];
        number[0] =  getLastStackPush(instList, lastStackOp);
        if (number[0] == null) {
            return null;
        }
        number[1] =  getLastStackPush(instList, lastStackOp);
        if (number[1] == null) {
            return null;
        }
        safeInstructionDelete(instList, lastStackOp);
        return number;
    }
}
