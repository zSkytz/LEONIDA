package net.minecraft.core; public class BlockPos { public int getX(){return 0;} public static class MutableBlockPos extends BlockPos { public MutableBlockPos set(int x,int y,int z){return this;} } }
