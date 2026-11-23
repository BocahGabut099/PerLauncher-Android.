package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class ElyByLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";

    public ElyByLoginFragment() {
        super(R.layout.fragment_elyby_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText username = view.findViewById(R.id.elyby_login_edit);
        EditText password = view.findViewById(R.id.elyby_password_edit);
        EditText totp = view.findViewById(R.id.elyby_totp_edit);

        view.findViewById(R.id.elyby_login_button).setOnClickListener(v -> {
            String login = username.getText().toString().trim();
            String pass = password.getText().toString();
            String code = totp.getText().toString().trim();
            if (login.isEmpty() || pass.isEmpty()) {
                Tools.dialog(requireContext(), getString(R.string.global_error),
                        getString(R.string.login_elyby_bad_credentials));
                return;
            }
            // Credentials are sent directly to Ely.by over HTTPS; only the issued tokens are saved.
            ExtraCore.setValue(ExtraConstants.ELYBY_LOGIN_TODO, new String[]{login, pass, code});
            Tools.backToMainMenu(requireActivity());
        });

        view.findViewById(R.id.elyby_register_button)
                .setOnClickListener(v -> Tools.openURL(requireActivity(), "https://account.ely.by/"));
    }
}
