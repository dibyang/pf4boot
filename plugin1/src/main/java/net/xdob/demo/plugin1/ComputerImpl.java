package net.xdob.demo.plugin1;

import net.xdob.pf4boot.annotation.Export;
import org.springframework.stereotype.Service;

/**
 * Computer2Impl
 *
 * @author yangzj
 * @version 1.0
 */
@Export
@Service
public class ComputerImpl implements Computer {
  @Override
  public double add(double n1, double n2) {
    return n1+n2;
  }
}
