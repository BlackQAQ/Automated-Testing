
import com.ibm.wala.analysis.typeInference.PrimitiveType;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;


public class TestingSelection {
    static ArrayList<Methods> changeMethods=new ArrayList<Methods>();
    static ArrayList<Classes> allClasses=new ArrayList<Classes>();
    static ArrayList<Methods> allMethods=new ArrayList<Methods>();
    static ArrayList<String> testClasses=new ArrayList<String>();
    static ArrayList<String> class_selection=new ArrayList<String>();
    static ArrayList<String> method_selection=new ArrayList<String>();
    public static void main(String[] args) throws CancelException, ClassHierarchyException, IOException, InvalidClassFileException {
        String operation=args[0];
        String project_target=args[1];
        String change_info=args[2];
        String dir="\\net\\mooctest";
        //构建分析域
        AnalysisScope scope= makeScope(project_target,dir);
        //生成CG
        CallGraph cg=makeCallGraph(scope);
        //遍历CG，将类和方法加载进列表
        iterateCG(cg);
        //获取变更信息
        getChanges(change_info);
        //依赖变更
        for(int i=0;i<allMethods.size();i++){
            mDependenciesChange(allMethods.get(i));
            cDependenciesChange(allMethods.get(i));
        }
        for(int i=0;i<allClasses.size();i++){
            Classes tmpClass=allClasses.get(i);
            for(int j=0;j<allMethods.size();j++){
                if(!allClasses.get(i).className.equals(allMethods.get(j).className)) continue;
                Methods tmpMethod=allMethods.get(j);
                for(int k=0;k<tmpMethod.cDependencies.size();k++){
                    if(tmpClass.cDependencies.contains(tmpMethod.cDependencies.get(k))) continue;
                    tmpClass.cDependencies.add(tmpMethod.cDependencies.get(k));
                }
                for(int k=0;k<tmpMethod.mDependencies.size();k++){
                    if(tmpClass.mDependencies.contains(tmpMethod.mDependencies.get(k))) continue;
                    tmpClass.mDependencies.add(tmpMethod.mDependencies.get(k));
                }
            }
        }
        //生成class-项目名.dot
        //classCFA();
        //生成method-项目名.dot
        //methodCFA();
        //类级测试用例选择
        if(operation.equals("-c"))
            classTestingSelection();
        //方法级测试用例选择
        else if(operation.equals("-m"))
            methodTestingSelection();
        else{
            System.out.println("Operation Error!");
        }


        /*类依赖传递测试
        for(int i=0;i<allClasses.size();i++) {
            Classes temp = allClasses.get(i);
            System.out.println(temp.className);
            for (int j = 0; j < temp.cDependencies.size(); j++) {
                System.out.println(temp.cDependencies.get(j).className);
            }
            System.out.println();
        }
        */

        /*方法依赖传递测试
        for(int i=0;i<allMethods.size();i++){
            Methods temp=allMethods.get(i);
            System.out.println(temp.className+" "+temp.methodName);
            for(int j=0;j<temp.mDependencies.size();j++){
                System.out.println(temp.mDependencies.get(j).className+" "+temp.mDependencies.get(j).methodName);
            }
            System.out.println();
            for(int j=0;j<temp.cDependencies.size();j++){
                System.out.println(temp.cDependencies.get(j).className);
            }
            System.out.println("------------------------------");
        }
        */

        //cha图测试
        //System.out.println();
        //for(IClass iClass:cha){
        //    if(iClass.toString().substring(0,iClass.toString().indexOf(",")).contains("Application"))
        //        System.out.println(iClass);
        //}
        //System.out.println();
        //String stats = CallGraphStats.getStats(cg);
        //System.out.println(stats);


        /*类和方法遍历测试
        for(int i=0;i<allClasses.size();i++){
            System.out.println(allClasses.get(i).className);
            for(int j=0;j<allClasses.get(i).mDependencies.size();j++){
                System.out.println(allClasses.get(i).mDependencies.get(j).className+" "+allClasses.get(i).mDependencies.get(j).methodName);
            }
            for(int k=0;k<allClasses.get(i).cDependencies.size();k++){
                System.out.println(allClasses.get(i).cDependencies.get(k).className);
            }
            System.out.println();
        }
        System.out.println();
        for(int i=0;i<allMethods.size();i++){
            System.out.println(allMethods.get(i).methodName);
            for(int j=0;j<allMethods.get(i).mDependencies.size();j++){
                System.out.println(allMethods.get(i).mDependencies.get(j).className+" "+allMethods.get(i).mDependencies.get(j).methodName);
            }
            for(int k=0;k<allMethods.get(i).cDependencies.size();k++){
                System.out.println(allMethods.get(i).cDependencies.get(k).className);
            }
            System.out.println();
        }
        */
    }

    //构建分析域
    public static AnalysisScope makeScope(String project_target,String dir) throws IOException, InvalidClassFileException {
        AnalysisScope scope= AnalysisScopeReader.readJavaScope(
                "scope.txt",new File("exclusion.txt"),ClassLoader.getSystemClassLoader());
        //System.out.println(scope);
        //读取文件列表
        File[] classes=(new File(project_target+"\\classes"+dir)).listFiles();
        File[] test_classes=(new File(project_target+"\\test-classes"+dir)).listFiles();
        //加入类
        for(File f:classes){
            scope.addClassFileToScope(ClassLoaderReference.Application,f);
            //System.out.println(f);
        }
        //加入测试类
        for(File f:test_classes){
            scope.addClassFileToScope(ClassLoaderReference.Application,f);
            testClasses.add(f.getName().substring(0,f.getName().length()-6));
            //System.out.println(f.getName().substring(0,f.getName().length()-6));
            //System.out.println(f);
        }
        return scope;
    }

    //生成CHA
    public static CallGraph makeCallGraph(AnalysisScope scope) throws ClassHierarchyException, CancelException {
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        // 生成进入点
        //Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);
        AllApplicationEntrypoints entrypoints=new AllApplicationEntrypoints(scope,cha);
        AnalysisOptions option=new AnalysisOptions(scope,entrypoints);
        SSAPropagationCallGraphBuilder builder= Util.makeZeroCFABuilder(
                Language.JAVA, option, new AnalysisCacheImpl(), cha, scope
        );
        CallGraph cg=builder.makeCallGraph(option,null);
        // 利用CHA算法构建调用图
        //CHACallGraph cg = new CHACallGraph(cha);
        //cg.init(eps);
        return cg;
    }

    public static void iterateCG(CallGraph cg){
        // 遍历cg中所有的节点
        for(CGNode node: cg) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            if(node.getMethod() instanceof ShrikeBTMethod) {
                // node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
                // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                // 使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
                if("Application".equals(method.getDeclaringClass().getClassLoader().toString())&&!method.getSignature().substring(0,4).equals("java")) {
                    Iterator<CGNode> iterator=cg.getPredNodes(node);
                    //向类和方法列表中加入node
                    boolean isInCList=false;
                    boolean isInMList=false;
                    for(int i=0;i<allClasses.size();i++){
                        if(allClasses.get(i).className.equals(node.getMethod().getDeclaringClass().getName().toString())){
                            isInCList=true;
                            break;
                        }
                    }
                    for(int i=0;i<allMethods.size();i++){
                        if(allMethods.get(i).methodName.equals(node.getMethod().getSignature())){
                            isInMList=true;
                            break;
                        }
                    }
                    if(!isInCList){
                        allClasses.add(new Classes(node.getMethod().getDeclaringClass().getName().toString()));
                    }
                    if(!isInMList){
                        allMethods.add(new Methods(node.getMethod().getSignature(),node.getMethod().getDeclaringClass().getName().toString()));
                    }
                    //System.out.println(node.getMethod().getDeclaringClass().getName().toString());
                    //System.out.println(node.getMethod().getSignature());
                    //System.out.println();
                    while(iterator.hasNext()){
                        CGNode temp=iterator.next();
                        if(temp.getMethod() instanceof ShrikeBTMethod){
                            if("Application".equals(method.getDeclaringClass().getClassLoader().toString())&&(!temp.getMethod().getSignature().substring(0,4).equals("java"))){
                                String cName=temp.getMethod().getDeclaringClass().getName().toString();
                                String mName=temp.getMethod().getSignature();
                                //Sys tem.out.println(cName);
                                //System.out.println(mName);
                                //向总列表中加入类和方法，并记录位置
                                isInCList=false;
                                isInMList=false;
                                int tcPos=0;
                                int tmPos=0;
                                for(;tcPos<allClasses.size();tcPos++){
                                    if(allClasses.get(tcPos).className.equals(cName)){
                                        isInCList=true;
                                        break;
                                    }
                                }
                                for(;tmPos<allMethods.size();tmPos++){
                                    if(allMethods.get(tmPos).methodName.equals(mName)){
                                        isInMList=true;
                                        break;
                                    }
                                }
                                if(!isInCList){
                                    allClasses.add(new Classes(cName));
                                }
                                if(!isInMList){
                                    allMethods.add(new Methods(mName,cName));
                                }
                                //获取类和方法的位置
                                int cPos=0;
                                int mPos=0;
                                for(;cPos<allClasses.size();cPos++){
                                    if (node.getMethod().getDeclaringClass().getName().toString().equals(allClasses.get(cPos).className))
                                        break;
                                }
                                for(;mPos<allMethods.size();mPos++){
                                    if(node.getMethod().getSignature().equals(allMethods.get(mPos).methodName))
                                        break;
                                }
                                //向类中添加调用者
                                isInCList=false;
                                isInMList=false;
                                for(int i=0;i<allClasses.get(cPos).cDependencies.size();i++){
                                    if(allClasses.get(cPos).cDependencies.get(i).className.equals(cName)){
                                        isInCList=true;
                                        break;
                                    }
                                }
                                for(int i=0;i<allClasses.get(cPos).mDependencies.size();i++){
                                    if(allClasses.get(cPos).mDependencies.get(i).className.equals(mName)) {
                                        isInMList=true;
                                        break;
                                    }
                                }
                                if(!isInCList){
                                    allClasses.get(cPos).cDependencies.add(allClasses.get(tcPos));
                                }
                                if(!isInMList){
                                    allClasses.get(cPos).mDependencies.add(allMethods.get(tmPos));
                                }
                                //向方法中添加调用者
                                isInCList=false;
                                isInMList=false;
                                for(int i=0;i<allMethods.get(mPos).cDependencies.size();i++){
                                    if(allMethods.get(mPos).cDependencies.get(i).className.equals(cName)){
                                        isInCList=true;
                                        break;
                                    }
                                }
                                for(int i=0;i<allMethods.get(mPos).mDependencies.size();i++){
                                    if(allMethods.get(mPos).mDependencies.get(i).className.equals(mName)) {
                                        isInMList=true;
                                        break;
                                    }
                                }
                                if(!isInCList){
                                    allMethods.get(mPos).cDependencies.add(allClasses.get(tcPos));
                                }
                                if(!isInMList){
                                    allMethods.get(mPos).mDependencies.add(allMethods.get(tmPos));
                                }
                            }
                        }
                    }
                    //System.out.println("----------------------------------");
                    // 获取声明该方法的类的内部表示
                    //String classInnerName =
                    //        method.getDeclaringClass().getName().toString();
                    //获取方法签名
                    //String signature = method.getSignature();
                    //System.out.println(classInnerName + " " + signature);
                }

            } /*else {
                System.out.println(String.format("'%s'不是一个ShrikeBTMethod：%s",
                        node.getMethod(),
                        node.getMethod().getClass()));
            }*/
        }
    }

    //获取变更的方法
    public static void getChanges(String change_info) throws IOException {
        File changeFile=new File(change_info);
        InputStreamReader reader=new InputStreamReader(new FileInputStream(changeFile));
        BufferedReader br=new BufferedReader(reader);
        String line="";
        line= br.readLine();
        //记录变更的方法
        while(line!=null){
            String[] names=line.split(" ");
            changeMethods.add(new Methods(names[1],names[0]));
            line=br.readLine();
        }
    }

    //方法依赖传递
    public static void mDependenciesChange(Methods cur){
        if(cur.isMSearched>=2) return;
        cur.isMSearched+=1;
        int[] isAdd=new int[allMethods.size()];
        for(int i=0;i<allMethods.size();i++){
            isAdd[i]=0;
        }
        for(int i=0;i<cur.mDependencies.size();i++){
            Methods d=cur.mDependencies.get(i);
            mDependenciesChange(d);
            for(int j=0;j<d.mDependencies.size();j++){
                for(int k=0;k<allMethods.size();k++){
                    if(d.mDependencies.get(j).methodName.equals(allMethods.get(k).methodName)){
                        isAdd[k]=1;
                        break;
                    }
                }
            }
        }
        for(int i=0;i<cur.mDependencies.size();i++){
            for(int j=0;j<allMethods.size();j++){
                if(cur.mDependencies.get(i).methodName.equals(allMethods.get(j).methodName)){
                    isAdd[j]=0;
                    break;
                }
            }
        }
        for(int i=0;i<allMethods.size();i++){
            if(isAdd[i]==1){
                cur.mDependencies.add(allMethods.get(i));
            }
        }
    }

    //类依赖传递
    public static void cDependenciesChange(Methods cur){
        if(cur.isCSearched>=2) return;
        cur.isCSearched+=1;
        int[] isAdd=new int[allClasses.size()];
        for(int i=0;i<allClasses.size();i++){
            isAdd[i]=0;
        }
        for(int i=0;i<cur.mDependencies.size();i++){
            Methods d=cur.mDependencies.get(i);
            cDependenciesChange(d);
            for(int j=0;j<d.cDependencies.size();j++){
                for(int k=0;k<allClasses.size();k++){
                    if(d.cDependencies.get(j).className.equals(allClasses.get(k).className)){
                        isAdd[k]=1;
                        break;
                    }
                }
            }
        }
        Classes curClass=new Classes(null);
        for(int i=0;i<allClasses.size();i++){
            if(allClasses.get(i).className.equals(cur.className)){
                curClass=allClasses.get(i);
                break;
            }
        }
        for(int i=0;i<cur.cDependencies.size();i++){
            for(int j=0;j<allClasses.size();j++){
                if(cur.cDependencies.get(i).className.equals(allClasses.get(j).className)){
                    isAdd[j]=0;
                    break;
                }
            }
        }
        for(int i=0;i<allClasses.size();i++){
            if(isAdd[i]==1){
                cur.cDependencies.add(allClasses.get(i));
            }
        }
        for(int i=0;i<curClass.cDependencies.size();i++){
            for(int j=0;j<allClasses.size();j++){
                if(curClass.cDependencies.get(i).className.equals(allClasses.get(j).className)){
                    isAdd[j]=0;
                    break;
                }
            }
        }
        for(int i=0;i<allClasses.size();i++){
            if(isAdd[i]==1){
                curClass.cDependencies.add(allClasses.get(i));
            }
        }
    }

    //生成类级.dot文件
    public static void classCFA() throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter("class-.dot"));
        out.write("digraph cmd_class {\r\n");
        System.out.print("digraph cmd_class {\r\n");
        for(int i=0;i<allClasses.size();i++){
            for(int j=0;j<allClasses.get(i).cDependencies.size();j++){
                out.write("\r\t\""+allClasses.get(i).className+"\" -> \""+allClasses.get(i).cDependencies.get(j).className+"\";\r\n");
                System.out.print("\t\""+allClasses.get(i).className+"\" -> \""+allClasses.get(i).cDependencies.get(j).className+"\";\r\n");
            }
        }
        out.write("}");
        System.out.println("\r}");
        out.close();
    }

    //生成方法级.dot文件
    public static void methodCFA() throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter("method-.dot"));
        out.write("digraph cmd_method {\r\n");
        //System.out.print("digraph cmd_method {\r\n");
        for(int i=0;i<allMethods.size();i++){
            for(int j=0;j<allMethods.get(i).mDependencies.size();j++){
                out.write("\r\t\""+allMethods.get(i).methodName+"\" -> \""+allMethods.get(i).mDependencies.get(j).methodName+"\";\r\n");
                //System.out.print("\t\""+allMethods.get(i).methodName+"\" -> \""+allMethods.get(i).mDependencies.get(j).methodName+"\";\r\n");
            }
        }
        out.write("}");
        //System.out.println("\r}");
        out.close();
    }

    //类级测试用例选择
    public static void classTestingSelection() throws IOException {
        ArrayList<String> classNames=new ArrayList<String>();
        for(int a=0;a<changeMethods.size();a++) {
            String changeEntry = changeMethods.get(a).className;
            if (classNames.contains(changeEntry)) continue;
            classNames.add(changeEntry);
        }
        for(int i=0;i<classNames.size();i++) {
            for(int j=0;j<allClasses.size();j++){
                if(!allClasses.get(j).className.equals(classNames.get(i))) continue;
                Classes curClass=allClasses.get(j);
                for(int k=0;k<curClass.cDependencies.size();k++){
                    boolean isTest=false;
                    for(int m=0;m<testClasses.size();m++){
                        if(curClass.cDependencies.get(k).className.contains(testClasses.get(m))){
                            isTest=true;
                            break;
                        }
                    }
                    if(!isTest) continue;
                    String cName=curClass.cDependencies.get(k).className;
                    for(int m=0;m<allMethods.size();m++){
                        if(!allMethods.get(m).className.equals(cName)) continue;
                        String mName=allMethods.get(m).methodName;
                        if(mName.contains("init")) continue;
                        String entry=cName + " " + mName + "\r\n";
                        if(class_selection.contains(entry)) continue;
                        class_selection.add(entry);
                    }
                }
            }
        }
        //写selection-class.txt文件
        BufferedWriter out = new BufferedWriter(new FileWriter("selection-class.txt"));
        for(int i=0;i<class_selection.size();i++){
            out.write(class_selection.get(i));
        }
        out.close();
    }

    //方法级测试用例选择
    public static void methodTestingSelection() throws IOException {
        for(int a=0;a<changeMethods.size();a++){
            String changeEntry=changeMethods.get(a).methodName;
            for(int i=0;i<allMethods.size();i++) {
                Methods cur = allMethods.get(i);
                String curEntry = cur.methodName;
                //判断是否选中
                if (!curEntry.equals(changeEntry)) continue;
                for (int j = 0; j < cur.mDependencies.size(); j++) {
                    String cName = cur.mDependencies.get(j).className;
                    String mName = cur.mDependencies.get(j).methodName;
                    String entry = cName + " " + mName + "\r\n";
                    boolean isTest = false;
                    //判断是否是测试用例
                    if(mName.contains("init")) continue;
                    for (int k = 0; k < testClasses.size(); k++) {
                        if (cName.contains(testClasses.get(k))) {
                            isTest = true;
                            break;
                        }
                    }
                    if (!isTest) continue;
                    boolean isInList = false;
                    //去重
                    for (int k = 0; k < method_selection.size(); k++) {
                        if (method_selection.get(k).equals(entry)) {
                            isInList = true;
                            break;
                        }
                    }
                    if (isInList) continue;
                    method_selection.add(entry);
                }
            }
        }
        //写selection-method.txt文件
        BufferedWriter out = new BufferedWriter(new FileWriter("selection-method.txt"));
        for(int i=0;i<method_selection.size();i++){
            out.write(method_selection.get(i));
        }
        out.close();
    }

}

//类
class Classes{
    String className="";
    ArrayList<Methods> mDependencies=new ArrayList<Methods>();
    ArrayList<Classes> cDependencies=new ArrayList<Classes>();

    public Classes(String name){
        this.className=name;
    }
}

//方法
class Methods{
    String methodName="";
    String className="";
    int isMSearched=0;
    int isCSearched=0;
    ArrayList<Methods> mDependencies=new ArrayList<Methods>();
    ArrayList<Classes> cDependencies=new ArrayList<Classes>();

    public Methods(String mName,String cName){

        this.methodName=mName;
        this.className=cName;

    }
}
