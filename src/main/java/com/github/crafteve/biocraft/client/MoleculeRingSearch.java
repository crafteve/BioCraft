package com.github.crafteve.biocraft.client;

import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.ringsearch.RingSearch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 环键连通分量的环质心计算（供环内双键朝内侧偏移）
 * <p>
 * 环键判定使用 CDK 的 RingSearch（专业环检测，语义标准可靠），
 * 覆盖芳香环（CDK Kekulize 后 isAromatic 保留）与显式 Kekulé 写法的
 * 非芳香环（如胞嘧啶/尿嘧啶的显式单双键环）
 */
final class MoleculeRingSearch {

    private MoleculeRingSearch() {
    }

    /**
     * 计算环键 -> 所属环分量质心 的映射
     *
     * @param molecule       分子
     * @param pixelPositions 原子坐标表
     * @return 环键 -> 所属环分量质心
     */
    static Map<IBond, double[]> ringCenters(IAtomContainer molecule, Map<IAtom, double[]> pixelPositions) {
        Map<IBond, double[]> centers = new HashMap<>();
        RingSearch ringSearch = new RingSearch(molecule);
        List<IBond> ringBonds = new ArrayList<>();
        for (IBond bond : molecule.bonds()) {
            if (!MoleculeGeometry.isHeavy(bond.getBegin()) || !MoleculeGeometry.isHeavy(bond.getEnd())) {
                continue;
            }
            if (ringSearch.cyclic(bond)) {
                ringBonds.add(bond);
            }
        }
        // 按共享原子分组（连通分量）
        Set<IBond> visited = new HashSet<>();
        for (IBond start : ringBonds) {
            if (!visited.add(start)) {
                continue;
            }
            List<IBond> component = new ArrayList<>();
            ArrayDeque<IBond> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                IBond bond = queue.poll();
                component.add(bond);
                for (IBond neighbor : ringBonds) {
                    // 共享端点原子判定相邻
                    boolean sharesAtom = neighbor.getBegin() == bond.getBegin()
                            || neighbor.getBegin() == bond.getEnd()
                            || neighbor.getEnd() == bond.getBegin()
                            || neighbor.getEnd() == bond.getEnd();
                    if (!visited.contains(neighbor) && sharesAtom) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
            // 分量内所有端点原子的平均坐标作为质心
            double sumX = 0, sumY = 0;
            int count = 0;
            Set<IAtom> atoms = new HashSet<>();
            for (IBond bond : component) {
                atoms.add(bond.getBegin());
                atoms.add(bond.getEnd());
            }
            for (IAtom atom : atoms) {
                double[] pos = pixelPositions.get(atom);
                if (pos != null) {
                    sumX += pos[0];
                    sumY += pos[1];
                    count++;
                }
            }
            double[] centroid = count > 0 ? new double[]{sumX / count, sumY / count} : new double[]{0, 0};
            for (IBond bond : component) {
                centers.put(bond, centroid);
            }
        }
        return centers;
    }
}
