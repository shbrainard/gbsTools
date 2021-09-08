
public interface RetainBehavior {
  boolean keepRead();
  
  static RetainBehavior KEEP_ALL = () -> true;
}
