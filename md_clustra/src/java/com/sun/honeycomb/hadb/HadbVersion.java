

package com.sun.honeycomb.hadb;

class HadbVersion implements Comparable {
    Integer major;
    Integer minor;
    Integer patch;
    Integer build;

    private static final int LESS = -1;
    private static final int EQUALS = 0;
    private static final int GREATER = 1;

    public HadbVersion(int a, int b, int c, int d) {
        major = new Integer(a);
        minor = new Integer(b);
        patch = new Integer(c);
        build = new Integer(d);
    }

    public HadbVersion(String s) {
        int len = s.length();
        if(s.charAt(len-1)=='/') {
            s = s.substring(0,len-1);
        }
        int slashLoc = s.lastIndexOf('/');
        if (slashLoc != -1) {
            s = s.substring(slashLoc+1);
        }
        //ok, now we should have just the version number, path stripped out
        String[] versions = s.split("[-\\.]");
        if (versions.length != 4) {
            throw new RuntimeException("Couldn't handle the HADB version string "+s);
        }
        major = new Integer(versions[0]);
        minor = new Integer(versions[1]);
        patch = new Integer(versions[2]);
        build = new Integer(versions[3]);
    }

    public int compareTo(Object o) {
        HadbVersion other = (HadbVersion)o;
        int tmp;
        tmp = major.compareTo(other.major);
        if (tmp != EQUALS) { return tmp; }
        tmp = minor.compareTo(other.minor);
        if (tmp != EQUALS) { return tmp; }
        tmp = patch.compareTo(other.patch);
        if (tmp != EQUALS) { return tmp; }
        tmp = build.compareTo(other.build);
        return tmp;
    }

    public String toPathString() {
        return String.valueOf(major)+"."+
            String.valueOf(minor)+"."+
            String.valueOf(patch)+"-"+String.valueOf(build);
    }

    public String toString() {
        return toPathString();
    }

    public String toPackageNameString() {
        return "V"+String.valueOf(major)+"."+
            String.valueOf(minor)+"."+
            String.valueOf(patch)+"."+String.valueOf(build);
    }

    public void go() throws Exception {
        HadbVersion one = new HadbVersion(1,2,3,4);
        HadbVersion two = new HadbVersion("/test/this/4.7.0.2");
        HadbVersion three = new HadbVersion("/opt/SUNWhadb/4.6.0.8");
        HadbVersion four = new HadbVersion("/config/hadb_install/4.5.0-11");
        HadbVersion five = new HadbVersion("/test/this/4.5.0-9");
        System.out.println(one.toPathString()+" "+one.toPackageNameString());
        System.out.println(two.toPathString()+" "+two.toPackageNameString());
        System.out.println(three.toPathString()+" "+three.toPackageNameString());
        System.out.println(four.toPathString()+" "+four.toPackageNameString());
        System.out.println(five.toPathString()+" "+five.toPackageNameString());
        System.out.println();
        System.out.println(four.compareTo(four));
        System.out.println(one.compareTo(two));
        System.out.println(four.compareTo(five));
        System.out.println(four.compareTo(three));
        System.out.println(three.compareTo(two));
        System.out.println();
        System.out.println(two.compareTo(one));
        System.out.println(five.compareTo(four));
        System.out.println(three.compareTo(four));
        System.out.println(two.compareTo(three));
        
    }

    public static void main(String[] args) throws Exception{
        System.out.println("hi");
        HadbVersion a = new HadbVersion(1,2,3,4);
        a.go();
    }
}

