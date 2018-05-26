package Mstar.IR.Operand;

import Mstar.IR.IIRVisitor;

public class PhysicalRegister extends Register {
    public String name;
    public boolean isCallerSave;
    public boolean isCalleeSave;

    @Override
    public void accept(IIRVisitor visitor) {
        visitor.visit(this);
    }
}
