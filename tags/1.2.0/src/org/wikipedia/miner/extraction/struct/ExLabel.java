// File generated by hadoop record compiler. Do not edit.
package org.wikipedia.miner.extraction.struct;

public class ExLabel extends org.apache.hadoop.record.Record {
  private static final org.apache.hadoop.record.meta.RecordTypeInfo _rio_recTypeInfo;
  private static org.apache.hadoop.record.meta.RecordTypeInfo _rio_rtiFilter;
  private static int[] _rio_rtiFilterFields;
  static {
    _rio_recTypeInfo = new org.apache.hadoop.record.meta.RecordTypeInfo("ExLabel");
    _rio_recTypeInfo.addField("LinkOccCount", org.apache.hadoop.record.meta.TypeID.LongTypeID);
    _rio_recTypeInfo.addField("LinkDocCount", org.apache.hadoop.record.meta.TypeID.LongTypeID);
    _rio_recTypeInfo.addField("TextOccCount", org.apache.hadoop.record.meta.TypeID.LongTypeID);
    _rio_recTypeInfo.addField("TextDocCount", org.apache.hadoop.record.meta.TypeID.LongTypeID);
    _rio_recTypeInfo.addField("SensesById", new org.apache.hadoop.record.meta.MapTypeID(org.apache.hadoop.record.meta.TypeID.IntTypeID, new org.apache.hadoop.record.meta.StructTypeID(org.wikipedia.miner.extraction.struct.ExSenseForLabel.getTypeInfo())));
  }
  
  private long LinkOccCount;
  private long LinkDocCount;
  private long TextOccCount;
  private long TextDocCount;
  private java.util.TreeMap<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel> SensesById;
  public ExLabel() { }
  public ExLabel(
    final long LinkOccCount,
    final long LinkDocCount,
    final long TextOccCount,
    final long TextDocCount,
    final java.util.TreeMap<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel> SensesById) {
    this.LinkOccCount = LinkOccCount;
    this.LinkDocCount = LinkDocCount;
    this.TextOccCount = TextOccCount;
    this.TextDocCount = TextDocCount;
    this.SensesById = SensesById;
  }
  public static org.apache.hadoop.record.meta.RecordTypeInfo getTypeInfo() {
    return _rio_recTypeInfo;
  }
  public static void setTypeFilter(org.apache.hadoop.record.meta.RecordTypeInfo rti) {
    if (null == rti) return;
    _rio_rtiFilter = rti;
    _rio_rtiFilterFields = null;
    org.wikipedia.miner.extraction.struct.ExSenseForLabel.setTypeFilter(rti.getNestedStructTypeInfo("ExSenseForLabel"));
  }
  private static void setupRtiFields()
  {
    if (null == _rio_rtiFilter) return;
    // we may already have done this
    if (null != _rio_rtiFilterFields) return;
    int _rio_i, _rio_j;
    _rio_rtiFilterFields = new int [_rio_rtiFilter.getFieldTypeInfos().size()];
    for (_rio_i=0; _rio_i<_rio_rtiFilterFields.length; _rio_i++) {
      _rio_rtiFilterFields[_rio_i] = 0;
    }
    java.util.Iterator<org.apache.hadoop.record.meta.FieldTypeInfo> _rio_itFilter = _rio_rtiFilter.getFieldTypeInfos().iterator();
    _rio_i=0;
    while (_rio_itFilter.hasNext()) {
      org.apache.hadoop.record.meta.FieldTypeInfo _rio_tInfoFilter = _rio_itFilter.next();
      java.util.Iterator<org.apache.hadoop.record.meta.FieldTypeInfo> _rio_it = _rio_recTypeInfo.getFieldTypeInfos().iterator();
      _rio_j=1;
      while (_rio_it.hasNext()) {
        org.apache.hadoop.record.meta.FieldTypeInfo _rio_tInfo = _rio_it.next();
        if (_rio_tInfo.equals(_rio_tInfoFilter)) {
          _rio_rtiFilterFields[_rio_i] = _rio_j;
          break;
        }
        _rio_j++;
      }
      _rio_i++;
    }
  }
  public long getLinkOccCount() {
    return LinkOccCount;
  }
  public void setLinkOccCount(final long LinkOccCount) {
    this.LinkOccCount=LinkOccCount;
  }
  public long getLinkDocCount() {
    return LinkDocCount;
  }
  public void setLinkDocCount(final long LinkDocCount) {
    this.LinkDocCount=LinkDocCount;
  }
  public long getTextOccCount() {
    return TextOccCount;
  }
  public void setTextOccCount(final long TextOccCount) {
    this.TextOccCount=TextOccCount;
  }
  public long getTextDocCount() {
    return TextDocCount;
  }
  public void setTextDocCount(final long TextDocCount) {
    this.TextDocCount=TextDocCount;
  }
  public java.util.TreeMap<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel> getSensesById() {
    return SensesById;
  }
  public void setSensesById(final java.util.TreeMap<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel> SensesById) {
    this.SensesById=SensesById;
  }
  public void serialize(final org.apache.hadoop.record.RecordOutput _rio_a, final String _rio_tag)
  throws java.io.IOException {
    _rio_a.startRecord(this,_rio_tag);
    _rio_a.writeLong(LinkOccCount,"LinkOccCount");
    _rio_a.writeLong(LinkDocCount,"LinkDocCount");
    _rio_a.writeLong(TextOccCount,"TextOccCount");
    _rio_a.writeLong(TextDocCount,"TextDocCount");
    {
      _rio_a.startMap(SensesById,"SensesById");
      java.util.Set<java.util.Map.Entry<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel>> _rio_es1 = SensesById.entrySet();
      for(java.util.Iterator<java.util.Map.Entry<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel>> _rio_midx1 = _rio_es1.iterator(); _rio_midx1.hasNext();) {
        java.util.Map.Entry<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel> _rio_me1 = _rio_midx1.next();
        int _rio_k1 = _rio_me1.getKey();
        org.wikipedia.miner.extraction.struct.ExSenseForLabel _rio_v1 = _rio_me1.getValue();
        _rio_a.writeInt(_rio_k1,"_rio_k1");
        _rio_v1.serialize(_rio_a,"_rio_v1");
      }
      _rio_a.endMap(SensesById,"SensesById");
    }
    _rio_a.endRecord(this,_rio_tag);
  }
  private void deserializeWithoutFilter(final org.apache.hadoop.record.RecordInput _rio_a, final String _rio_tag)
  throws java.io.IOException {
    _rio_a.startRecord(_rio_tag);
    LinkOccCount=_rio_a.readLong("LinkOccCount");
    LinkDocCount=_rio_a.readLong("LinkDocCount");
    TextOccCount=_rio_a.readLong("TextOccCount");
    TextDocCount=_rio_a.readLong("TextDocCount");
    {
      org.apache.hadoop.record.Index _rio_midx1 = _rio_a.startMap("SensesById");
      SensesById=new java.util.TreeMap<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel>();
      for (; !_rio_midx1.done(); _rio_midx1.incr()) {
        int _rio_k1;
        _rio_k1=_rio_a.readInt("_rio_k1");
        org.wikipedia.miner.extraction.struct.ExSenseForLabel _rio_v1;
        _rio_v1= new org.wikipedia.miner.extraction.struct.ExSenseForLabel();
        _rio_v1.deserialize(_rio_a,"_rio_v1");
        SensesById.put(_rio_k1,_rio_v1);
      }
      _rio_a.endMap("SensesById");
    }
    _rio_a.endRecord(_rio_tag);
  }
  public void deserialize(final org.apache.hadoop.record.RecordInput _rio_a, final String _rio_tag)
  throws java.io.IOException {
    if (null == _rio_rtiFilter) {
      deserializeWithoutFilter(_rio_a, _rio_tag);
      return;
    }
    // if we're here, we need to read based on version info
    _rio_a.startRecord(_rio_tag);
    setupRtiFields();
    for (int _rio_i=0; _rio_i<_rio_rtiFilter.getFieldTypeInfos().size(); _rio_i++) {
      if (1 == _rio_rtiFilterFields[_rio_i]) {
        LinkOccCount=_rio_a.readLong("LinkOccCount");
      }
      else if (2 == _rio_rtiFilterFields[_rio_i]) {
        LinkDocCount=_rio_a.readLong("LinkDocCount");
      }
      else if (3 == _rio_rtiFilterFields[_rio_i]) {
        TextOccCount=_rio_a.readLong("TextOccCount");
      }
      else if (4 == _rio_rtiFilterFields[_rio_i]) {
        TextDocCount=_rio_a.readLong("TextDocCount");
      }
      else if (5 == _rio_rtiFilterFields[_rio_i]) {
        {
          org.apache.hadoop.record.Index _rio_midx1 = _rio_a.startMap("SensesById");
          SensesById=new java.util.TreeMap<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel>();
          for (; !_rio_midx1.done(); _rio_midx1.incr()) {
            int _rio_k1;
            _rio_k1=_rio_a.readInt("_rio_k1");
            org.wikipedia.miner.extraction.struct.ExSenseForLabel _rio_v1;
            _rio_v1= new org.wikipedia.miner.extraction.struct.ExSenseForLabel();
            _rio_v1.deserialize(_rio_a,"_rio_v1");
            SensesById.put(_rio_k1,_rio_v1);
          }
          _rio_a.endMap("SensesById");
        }
      }
      else {
        java.util.ArrayList<org.apache.hadoop.record.meta.FieldTypeInfo> typeInfos = (java.util.ArrayList<org.apache.hadoop.record.meta.FieldTypeInfo>)(_rio_rtiFilter.getFieldTypeInfos());
        org.apache.hadoop.record.meta.Utils.skip(_rio_a, typeInfos.get(_rio_i).getFieldID(), typeInfos.get(_rio_i).getTypeID());
      }
    }
    _rio_a.endRecord(_rio_tag);
  }
  public int compareTo (final Object _rio_peer_) throws ClassCastException {
    if (!(_rio_peer_ instanceof ExLabel)) {
      throw new ClassCastException("Comparing different types of records.");
    }
    ExLabel _rio_peer = (ExLabel) _rio_peer_;
    int _rio_ret = 0;
    _rio_ret = (LinkOccCount == _rio_peer.LinkOccCount)? 0 :((LinkOccCount<_rio_peer.LinkOccCount)?-1:1);
    if (_rio_ret != 0) return _rio_ret;
    _rio_ret = (LinkDocCount == _rio_peer.LinkDocCount)? 0 :((LinkDocCount<_rio_peer.LinkDocCount)?-1:1);
    if (_rio_ret != 0) return _rio_ret;
    _rio_ret = (TextOccCount == _rio_peer.TextOccCount)? 0 :((TextOccCount<_rio_peer.TextOccCount)?-1:1);
    if (_rio_ret != 0) return _rio_ret;
    _rio_ret = (TextDocCount == _rio_peer.TextDocCount)? 0 :((TextDocCount<_rio_peer.TextDocCount)?-1:1);
    if (_rio_ret != 0) return _rio_ret;
    {
      java.util.Set<Integer> _rio_set10 = SensesById.keySet();
      java.util.Set<Integer> _rio_set20 = _rio_peer.SensesById.keySet();
      java.util.Iterator<Integer> _rio_miter10 = _rio_set10.iterator();
      java.util.Iterator<Integer> _rio_miter20 = _rio_set20.iterator();
      for(; _rio_miter10.hasNext() && _rio_miter20.hasNext();) {
        int _rio_k10 = _rio_miter10.next();
        int _rio_k20 = _rio_miter20.next();
        _rio_ret = (_rio_k10 == _rio_k20)? 0 :((_rio_k10<_rio_k20)?-1:1);
        if (_rio_ret != 0) { return _rio_ret; }
      }
      _rio_ret = (_rio_set10.size() - _rio_set20.size());
    }
    if (_rio_ret != 0) return _rio_ret;
    return _rio_ret;
  }
  public boolean equals(final Object _rio_peer_) {
    if (!(_rio_peer_ instanceof ExLabel)) {
      return false;
    }
    if (_rio_peer_ == this) {
      return true;
    }
    ExLabel _rio_peer = (ExLabel) _rio_peer_;
    boolean _rio_ret = false;
    _rio_ret = (LinkOccCount==_rio_peer.LinkOccCount);
    if (!_rio_ret) return _rio_ret;
    _rio_ret = (LinkDocCount==_rio_peer.LinkDocCount);
    if (!_rio_ret) return _rio_ret;
    _rio_ret = (TextOccCount==_rio_peer.TextOccCount);
    if (!_rio_ret) return _rio_ret;
    _rio_ret = (TextDocCount==_rio_peer.TextDocCount);
    if (!_rio_ret) return _rio_ret;
    _rio_ret = SensesById.equals(_rio_peer.SensesById);
    if (!_rio_ret) return _rio_ret;
    return _rio_ret;
  }
  public Object clone() throws CloneNotSupportedException {
    ExLabel _rio_other = new ExLabel();
    _rio_other.LinkOccCount = this.LinkOccCount;
    _rio_other.LinkDocCount = this.LinkDocCount;
    _rio_other.TextOccCount = this.TextOccCount;
    _rio_other.TextDocCount = this.TextDocCount;
    _rio_other.SensesById = (java.util.TreeMap<Integer,org.wikipedia.miner.extraction.struct.ExSenseForLabel>) this.SensesById.clone();
    return _rio_other;
  }
  public int hashCode() {
    int _rio_result = 17;
    int _rio_ret;
    _rio_ret = (int) (LinkOccCount^(LinkOccCount>>>32));
    _rio_result = 37*_rio_result + _rio_ret;
    _rio_ret = (int) (LinkDocCount^(LinkDocCount>>>32));
    _rio_result = 37*_rio_result + _rio_ret;
    _rio_ret = (int) (TextOccCount^(TextOccCount>>>32));
    _rio_result = 37*_rio_result + _rio_ret;
    _rio_ret = (int) (TextDocCount^(TextDocCount>>>32));
    _rio_result = 37*_rio_result + _rio_ret;
    _rio_ret = SensesById.hashCode();
    _rio_result = 37*_rio_result + _rio_ret;
    return _rio_result;
  }
  public static String signature() {
    return "LExLabel(llll{iLExSenseForLabel(llzz)})";
  }
  public static class Comparator extends org.apache.hadoop.record.RecordComparator {
    public Comparator() {
      super(ExLabel.class);
    }
    static public int slurpRaw(byte[] b, int s, int l) {
      try {
        int os = s;
        {
          long i = org.apache.hadoop.record.Utils.readVLong(b, s);
          int z = org.apache.hadoop.record.Utils.getVIntSize(i);
          s+=z; l-=z;
        }
        {
          long i = org.apache.hadoop.record.Utils.readVLong(b, s);
          int z = org.apache.hadoop.record.Utils.getVIntSize(i);
          s+=z; l-=z;
        }
        {
          long i = org.apache.hadoop.record.Utils.readVLong(b, s);
          int z = org.apache.hadoop.record.Utils.getVIntSize(i);
          s+=z; l-=z;
        }
        {
          long i = org.apache.hadoop.record.Utils.readVLong(b, s);
          int z = org.apache.hadoop.record.Utils.getVIntSize(i);
          s+=z; l-=z;
        }
        {
          int mi1 = org.apache.hadoop.record.Utils.readVInt(b, s);
          int mz1 = org.apache.hadoop.record.Utils.getVIntSize(mi1);
          s+=mz1; l-=mz1;
          for (int midx1 = 0; midx1 < mi1; midx1++) {{
              int i = org.apache.hadoop.record.Utils.readVInt(b, s);
              int z = org.apache.hadoop.record.Utils.getVIntSize(i);
              s+=z; l-=z;
            }
            {
              int r = org.wikipedia.miner.extraction.struct.ExSenseForLabel.Comparator.slurpRaw(b,s,l);
              s+=r; l-=r;
            }
          }
        }
        return (os - s);
      } catch(java.io.IOException e) {
        throw new RuntimeException(e);
      }
    }
    static public int compareRaw(byte[] b1, int s1, int l1,
                                   byte[] b2, int s2, int l2) {
      try {
        int os1 = s1;
        {
          long i1 = org.apache.hadoop.record.Utils.readVLong(b1, s1);
          long i2 = org.apache.hadoop.record.Utils.readVLong(b2, s2);
          if (i1 != i2) {
            return ((i1-i2) < 0) ? -1 : 0;
          }
          int z1 = org.apache.hadoop.record.Utils.getVIntSize(i1);
          int z2 = org.apache.hadoop.record.Utils.getVIntSize(i2);
          s1+=z1; s2+=z2; l1-=z1; l2-=z2;
        }
        {
          long i1 = org.apache.hadoop.record.Utils.readVLong(b1, s1);
          long i2 = org.apache.hadoop.record.Utils.readVLong(b2, s2);
          if (i1 != i2) {
            return ((i1-i2) < 0) ? -1 : 0;
          }
          int z1 = org.apache.hadoop.record.Utils.getVIntSize(i1);
          int z2 = org.apache.hadoop.record.Utils.getVIntSize(i2);
          s1+=z1; s2+=z2; l1-=z1; l2-=z2;
        }
        {
          long i1 = org.apache.hadoop.record.Utils.readVLong(b1, s1);
          long i2 = org.apache.hadoop.record.Utils.readVLong(b2, s2);
          if (i1 != i2) {
            return ((i1-i2) < 0) ? -1 : 0;
          }
          int z1 = org.apache.hadoop.record.Utils.getVIntSize(i1);
          int z2 = org.apache.hadoop.record.Utils.getVIntSize(i2);
          s1+=z1; s2+=z2; l1-=z1; l2-=z2;
        }
        {
          long i1 = org.apache.hadoop.record.Utils.readVLong(b1, s1);
          long i2 = org.apache.hadoop.record.Utils.readVLong(b2, s2);
          if (i1 != i2) {
            return ((i1-i2) < 0) ? -1 : 0;
          }
          int z1 = org.apache.hadoop.record.Utils.getVIntSize(i1);
          int z2 = org.apache.hadoop.record.Utils.getVIntSize(i2);
          s1+=z1; s2+=z2; l1-=z1; l2-=z2;
        }
        {
          int mi11 = org.apache.hadoop.record.Utils.readVInt(b1, s1);
          int mi21 = org.apache.hadoop.record.Utils.readVInt(b2, s2);
          int mz11 = org.apache.hadoop.record.Utils.getVIntSize(mi11);
          int mz21 = org.apache.hadoop.record.Utils.getVIntSize(mi21);
          s1+=mz11; s2+=mz21; l1-=mz11; l2-=mz21;
          for (int midx1 = 0; midx1 < mi11 && midx1 < mi21; midx1++) {{
              int i1 = org.apache.hadoop.record.Utils.readVInt(b1, s1);
              int i2 = org.apache.hadoop.record.Utils.readVInt(b2, s2);
              if (i1 != i2) {
                return ((i1-i2) < 0) ? -1 : 0;
              }
              int z1 = org.apache.hadoop.record.Utils.getVIntSize(i1);
              int z2 = org.apache.hadoop.record.Utils.getVIntSize(i2);
              s1+=z1; s2+=z2; l1-=z1; l2-=z2;
            }
            {
              int r = org.wikipedia.miner.extraction.struct.ExSenseForLabel.Comparator.slurpRaw(b1,s1,l1);
              s1+=r; l1-=r;
            }
            {
              int r = org.wikipedia.miner.extraction.struct.ExSenseForLabel.Comparator.slurpRaw(b2,s2,l2);
              s2+=r; l2-=r;
            }
          }
          if (mi11 != mi21) { return (mi11<mi21)?-1:0; }
        }
        return (os1 - s1);
      } catch(java.io.IOException e) {
        throw new RuntimeException(e);
      }
    }
    public int compare(byte[] b1, int s1, int l1,
                         byte[] b2, int s2, int l2) {
      int ret = compareRaw(b1,s1,l1,b2,s2,l2);
      return (ret == -1)? -1 : ((ret==0)? 1 : 0);}
  }
  
  static {
    org.apache.hadoop.record.RecordComparator.define(ExLabel.class, new Comparator());
  }
}
