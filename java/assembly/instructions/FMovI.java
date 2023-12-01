package assembly.instructions;

/**
 * Class corresponding to RISC-V IMOVF instruction
 * 
 * Models: imovf dest src #dest = 
 */
public class FMovI extends Instruction {

    /**
     * Initializes a Float Conversion instruction that will print: FMovI dest src
     * 
     * @param src source operand 1
     * @param dest destination operand
     */

    public FMovI(String src, String dest) {
        super();
        this.src1 = src;
        this.dest = dest;
        this.oc = OpCode.FMOVI;
    }

    /**
     * @return "FMovI dest src"
     */
    public String toString() {
        return this.oc + " " + this.dest + ", " + this.src1;
    }
}