# 通用 Android 规则
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留 Application 类
-keep class com.unlockmusic.android.App { *; }

# 保留 Activity
-keep class com.unlockmusic.android.ui.MainActivity { *; }

# 保留 ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }

# 保留数据类（被 Gson/Bundle 使用）
-keep class com.unlockmusic.android.model.** { *; }
-keep class com.unlockmusic.android.decrypt.DecryptionResult { *; }

# 保留 ViewBinding 生成的类
-keep class * implements androidx.viewbinding.ViewBinding { *; }

# 保留 R 类
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留自定义 View 的构造函数
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 移除日志
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
