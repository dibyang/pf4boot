package com.ls.plugin1;

import com.ls.pf4boot.autoconfigure.ShareService;
import org.springframework.stereotype.Service;

/**
 * Computer2Impl
 *
 * @author yangzj
 * @version 1.0
 */
@ShareService
public class Computer2Impl implements Computer2 {
  @Override
  public double add2(double n1, double n2) {
    return n1+n2;
  }
}
