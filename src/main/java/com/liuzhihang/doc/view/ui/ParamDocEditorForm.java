package com.liuzhihang.doc.view.ui;

import com.intellij.find.editorHeaderActions.Utils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.ui.WindowMoveListener;
import com.intellij.util.ui.JBUI;
import com.liuzhihang.doc.view.DocViewBundle;
import com.liuzhihang.doc.view.config.Settings;
import com.liuzhihang.doc.view.config.SettingsConfigurable;
import com.liuzhihang.doc.view.config.TagsSettings;
import com.liuzhihang.doc.view.dto.Body;
import com.liuzhihang.doc.view.dto.DocViewData;
import com.liuzhihang.doc.view.dto.ParamData;
import com.liuzhihang.doc.view.service.impl.WriterService;
import com.liuzhihang.doc.view.utils.GsonFormatUtil;
import com.liuzhihang.doc.view.utils.NotificationUtils;
import com.liuzhihang.doc.view.utils.ParamPsiUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author liuzhihang
 * @date 2021/3/31 10:49
 */
public class ParamDocEditorForm {

    private final Project project;
    private final PsiFile psiFile;
    private final Editor editor;
    private final PsiClass psiClass;

    private final ParamTableModel paramTableModel;

    private JPanel rootPanel;
    private JPanel headToolbarPanel;
    private JPanel tailToolbarPanel;

    private JScrollPane paramScrollPane;
    private JTable paramTable;



    private JBPopup popup;


    @NonNls
    public static final String DOC_VIEW_POPUP = "com.intellij.docview.param.editor.popup";
    private static final AtomicBoolean myIsPinned = new AtomicBoolean(true);

    private final WriterService writerService = ServiceManager.getService(WriterService.class);

    public ParamDocEditorForm(@NotNull Project project, @NotNull PsiFile psiFile,
                              @NotNull Editor editor, @NotNull PsiClass psiClass) {

        this.project = project;
        this.psiFile = psiFile;
        this.editor = editor;
        this.psiClass = psiClass;

        List<Body> bodyList = ParamPsiUtils.buildBodyList(Settings.getInstance(project), psiClass, null);

        paramTableModel = new ParamTableModel(DocViewData.buildBodyDataList(bodyList));

        // UI调整
        initUI();
        initHeadToolbar();
        initTailLeftToolbar();
        initTailRightToolbar();

        buildParamTable();
        addMouseListeners();
    }

    @NotNull
    @Contract("_, _, _, _ -> new")
    public static ParamDocEditorForm getInstance(@NotNull Project project, @NotNull PsiFile psiFile,
                                                 @NotNull Editor editor, @NotNull PsiClass psiClass) {
        return new ParamDocEditorForm(project, psiFile, editor, psiClass);
    }


    private void addMouseListeners() {
        WindowMoveListener windowMoveListener = new WindowMoveListener(rootPanel);
        rootPanel.addMouseListener(windowMoveListener);
        rootPanel.addMouseMotionListener(windowMoveListener);

    }

    public void popup() {

        // dialog 改成 popup, 第一个为根面板，第二个为焦点面板
        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(rootPanel, paramScrollPane)
                .setProject(project)
                .setResizable(true)
                .setMovable(true)

                .setModalContext(false)
                .setRequestFocus(true)
                .setBelongsToGlobalPopupStack(true)
                .setDimensionServiceKey(null, DOC_VIEW_POPUP, true)
                .setLocateWithinScreenBounds(false)
                // 鼠标点击外部时是否取消弹窗 外部单击, 未处于 pin 状态则可关闭
                .setCancelOnMouseOutCallback(event -> event.getID() == MouseEvent.MOUSE_PRESSED && !myIsPinned.get())

                // 单击外部时取消弹窗
                .setCancelOnClickOutside(false)
                // 在其他窗口打开时取消
                .setCancelOnOtherWindowOpen(false)
                .setCancelOnWindowDeactivation(false)
                .createPopup();
        popup.showCenteredInCurrentWindow(project);

    }


    private void initUI() {
        // 禁止头部重排
        JTableHeader tableHeader = paramTable.getTableHeader();
        tableHeader.setReorderingAllowed(false);

        // 边框
        paramScrollPane.setBorder(JBUI.Borders.empty());
        paramTable.setBorder(JBUI.Borders.empty());
    }

    private void initHeadToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();


        group.add(new AnAction("Refresh", "Refresh", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // TODO: 2021/3/31 刷新
            }
        });

        group.add(new AnAction("Setting", "Doc view settings", AllIcons.General.GearPlain) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), SettingsConfigurable.class);
            }
        });

        group.addSeparator();

        group.add(new ToggleAction("Pin", "Pin window", AllIcons.General.Pin_tab) {

            @Override
            public boolean isDumbAware() {
                return true;
            }

            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return myIsPinned.get();
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                myIsPinned.set(state);
            }
        });

        ActionToolbarImpl toolbar = (ActionToolbarImpl) ActionManager.getInstance()
                .createActionToolbar("ParamDocParamEditorHeadToolbar", group, true);
        toolbar.setTargetComponent(headToolbarPanel);

        toolbar.setForceMinimumSize(true);
        toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
        Utils.setSmallerFontForChildren(toolbar);

        headToolbarPanel.add(toolbar.getComponent(), BorderLayout.EAST);
    }

    /**
     * 底部 右侧 主要是提交
     */
    private void initTailRightToolbar() {

        DefaultActionGroup rightGroup = new DefaultActionGroup();

        rightGroup.add(new AnAction("Copy as Json", "Copy as json", AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

                Map<String, Object> fieldMap = ParamPsiUtils.getFieldsAndDefaultValue(psiClass, null);
                String format = GsonFormatUtil.gsonFormat(fieldMap);
                StringSelection selection = new StringSelection(format);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);
                NotificationUtils.infoNotify(DocViewBundle.message("param.copy.success", psiClass.getName()), project);
                popup.cancel();
            }
        });

        rightGroup.add(new AnAction("Confirm", "Confirm modification", AllIcons.Actions.Commit) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {

                if (paramTable.isEditing()) {
                    paramTable.getCellEditor().stopCellEditing();
                }
                generateComment();
                popup.cancel();
            }
        });


        // init toolbar
        ActionToolbarImpl toolbar = (ActionToolbarImpl) ActionManager.getInstance()
                .createActionToolbar("ParamDocParamEditorTailRightToolbar", rightGroup, true);
        toolbar.setTargetComponent(tailToolbarPanel);

        toolbar.setForceMinimumSize(true);
        toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
        Utils.setSmallerFontForChildren(toolbar);

        tailToolbarPanel.add(toolbar.getComponent(), BorderLayout.EAST);

    }

    /**
     * 变动的字段生成注释
     */
    private void generateComment() {

        TagsSettings tagsSettings = TagsSettings.getInstance(project);

        Map<PsiElement, ParamData> modifyBodyMap = paramTableModel.getModifyBodyMap();

        for (PsiElement element : modifyBodyMap.keySet()) {
            ParamData data = modifyBodyMap.get(element);
            String comment;

            if (data.getRequired()) {
                comment = "/** "
                        + data.getDesc() + "\n"
                        + "* @" + tagsSettings.getRequired()
                        + " */";
            } else {
                comment = "/** "
                        + data.getDesc()
                        + " */";
            }


            PsiElementFactory factory = PsiElementFactory.getInstance(project);
            PsiDocComment psiDocComment = factory.createDocCommentFromText(comment);
            writerService.write(project, element, psiDocComment);
        }
    }

    private void initTailLeftToolbar() {

    }


    private void buildParamTable() {


        paramTable.setModel(paramTableModel);

        ParamTableUI.rowSetting(paramTable);
        ParamTableUI.columnSetting(paramTable);

        // 数据改变监听
        paramTableModel.addTableModelListener(e -> {

            ParamTableModel model = (ParamTableModel) e.getSource();
        });

    }

}
