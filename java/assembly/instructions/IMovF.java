package assembly.instructions;

/**
 * Class corresponding to RISC-V IMOVF instruction
 * 
 * Models: imovf dest src #dest = 
 */
public class IMovF extends Instruction {

    /**
     * Initializes a Float Conversion instruction that will print: IMovF dest src
     * 
     * @param src source operand 1
     * @param dest destination operand
     */

    public IMovF(String src, String dest) {
        super();
        this.src1 = src;
        this.dest = dest;
        this.oc = OpCode.IMOVF;
    }

    /**
     * @return "IMovF dest src"
     */
    public String toString() {
        return this.oc + " " + this.dest + ", " + this.src1;
    }
}