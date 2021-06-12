package org.serratec.ecommerce.services;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.mail.MessagingException;

import org.serratec.ecommerce.dto.ClienteDTO;
import org.serratec.ecommerce.dto.ClienteDTONovo;
import org.serratec.ecommerce.entities.ClienteEntity;
import org.serratec.ecommerce.exceptions.AtributoEncontradoException;
import org.serratec.ecommerce.exceptions.ClienteNotFoundException;
import org.serratec.ecommerce.mapper.ClienteMapper;
import org.serratec.ecommerce.repositories.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Service
public class ClienteService {

	@Autowired
	ClienteRepository repository;
	
	@Autowired
	ClienteMapper mapper;
	
	@Autowired
	BCryptPasswordEncoder bCrypt;
	
	@Autowired
	EmailSenderService emailSender;
	
	public List<ClienteDTO> getAll() {
		List<ClienteEntity> listaCliente = repository.findAllByAtivoTrue();
		List<ClienteDTO> listaDTO = new ArrayList<>();
		for (ClienteEntity clienteEntity : listaCliente) {
			listaDTO.add(mapper.entityToDTO(clienteEntity));
		}
		return listaDTO;
	}
	
	public ClienteEntity findByUserNameOrEmail(String userNameOrEmail) throws ClienteNotFoundException { 
		Optional<ClienteEntity> cliente = repository.findByAtivoTrueAndUserNameOrEmailOrCpf(userNameOrEmail, userNameOrEmail, userNameOrEmail); 
		if (cliente.isPresent()) { 
			return cliente.get(); 
		}
		throw new ClienteNotFoundException("Cliente não encontrado!"); 
	}
	
	public ClienteDTO findByUserNameOrEmailDTO(String userNameOrEmail) throws ClienteNotFoundException { 
		Optional<ClienteEntity> cliente = repository.findByAtivoTrueAndUserNameOrEmailOrCpf(userNameOrEmail, userNameOrEmail, userNameOrEmail); 
		if (cliente.isPresent()) { 
			return mapper.entityToDTO(cliente.get());
		}
		throw new ClienteNotFoundException("Cliente não encontrado!"); 
	}

	public ClienteDTO create(ClienteDTONovo novoCliente) throws AtributoEncontradoException, MessagingException {
		ClienteEntity entity = mapper.clienteDTOnovoToEntity(novoCliente);	
		Optional<ClienteEntity> userName = repository.findByUserNameOrEmailOrCpf(entity.getUserName(), entity.getUserName(), entity.getUserName());
		Optional<ClienteEntity> email = repository.findByUserNameOrEmailOrCpf(entity.getEmail(), entity.getEmail(), entity.getEmail());
		Optional<ClienteEntity> cpf = repository.findByUserNameOrEmailOrCpf(entity.getCpf(), entity.getCpf(), entity.getCpf());
		if (userName.isPresent()) throw new AtributoEncontradoException("UserName já utilizado!");
		if (email.isPresent() || cpf.isPresent()) throw new AtributoEncontradoException("Email já cadastrado!");
		if (cpf.isPresent()) throw new AtributoEncontradoException("CPF já cadastrado!");
		entity.setAtivo(true);
		var corpo = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\"><head><meta charset=\"UTF-8\"><meta content=\"width=device-width, initial-scale=1\" name=\"viewport\"><meta name=\"x-apple-disable-message-reformatting\"><meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"><meta content=\"telephone=no\" name=\"format-detection\"><title>Bem vindo</title> <!--[if (mso 16)]><style type=\"text/css\"> a {text-decoration: none;} </style><![endif]--> <!--[if gte mso 9]><style>sup { font-size: 100% !important; }</style><![endif]--> <!--[if gte mso 9]><xml> <o:OfficeDocumentSettings> <o:AllowPNG></o:AllowPNG> <o:PixelsPerInch>96</o:PixelsPerInch> </o:OfficeDocumentSettings> </xml><![endif]--><style type=\"text/css\">#outlook a { padding:0;}.es-button { mso-style-priority:100!important; text-decoration:none!important;}a[x-apple-data-detectors] { color:inherit!important; text-decoration:none!important; font-size:inherit!important; font-family:inherit!important; font-weight:inherit!important; line-height:inherit!important;}.es-desk-hidden { display:none; float:left; overflow:hidden; width:0; max-height:0; line-height:0; mso-hide:all;}[data-ogsb] .es-button { border-width:0!important; padding:10px 20px 10px 20px!important;}@media only screen and (max-width:600px) {p, ul li, ol li, a { line-height:150%!important } h1 { font-size:30px!important; text-align:center; line-height:120%!important } h2 { font-size:26px!important; text-align:center; line-height:120%!important } h3 { font-size:20px!important; text-align:center; line-height:120%!important } .es-header-body h1 a, .es-content-body h1 a, .es-footer-body h1 a { font-size:30px!important } .es-header-body h2 a, .es-content-body h2 a, .es-footer-body h2 a { font-size:26px!important } .es-header-body h3 a, .es-content-body h3 a, .es-footer-body h3 a { font-size:20px!important } .es-menu td a { font-size:16px!important } .es-header-body p, .es-header-body ul li, .es-header-body ol li, .es-header-body a { font-size:16px!important } .es-content-body p, .es-content-body ul li, .es-content-body ol li, .es-content-body a { font-size:16px!important } .es-footer-body p, .es-footer-body ul li, .es-footer-body ol li, .es-footer-body a { font-size:16px!important } .es-infoblock p, .es-infoblock ul li, .es-infoblock ol li, .es-infoblock a { font-size:12px!important } *[class=\"gmail-fix\"] { display:none!important } .es-m-txt-c, .es-m-txt-c h1, .es-m-txt-c h2, .es-m-txt-c h3 { text-align:center!important } .es-m-txt-r, .es-m-txt-r h1, .es-m-txt-r h2, .es-m-txt-r h3 { text-align:right!important } .es-m-txt-l, .es-m-txt-l h1, .es-m-txt-l h2, .es-m-txt-l h3 { text-align:left!important } .es-m-txt-r img, .es-m-txt-c img, .es-m-txt-l img { display:inline!important } .es-button-border { display:block!important } a.es-button, button.es-button { font-size:20px!important; display:block!important; border-width:10px 0px 10px 0px!important } .es-adaptive table, .es-left, .es-right { width:100%!important } .es-content table, .es-header table, .es-footer table, .es-content, .es-footer, .es-header { width:100%!important; max-width:600px!important } .es-adapt-td { display:block!important; width:100%!important } .adapt-img { width:100%!important; height:auto!important } .es-m-p0 { padding:0px!important } .es-m-p0r { padding-right:0px!important } .es-m-p0l { padding-left:0px!important } .es-m-p0t { padding-top:0px!important } .es-m-p0b { padding-bottom:0!important } .es-m-p20b { padding-bottom:20px!important } .es-mobile-hidden, .es-hidden { display:none!important } tr.es-desk-hidden, td.es-desk-hidden, table.es-desk-hidden { width:auto!important; overflow:visible!important; float:none!important; max-height:inherit!important; line-height:inherit!important } tr.es-desk-hidden { display:table-row!important } table.es-desk-hidden { display:table!important } td.es-desk-menu-hidden { display:table-cell!important } .es-menu td { width:1%!important } table.es-table-not-adapt, .esd-block-html table { width:auto!important } table.es-social { display:inline-block!important } table.es-social td { display:inline-block!important } }</style></head>\r\n"
				+ "<body style=\"width:100%;font-family:arial, 'helvetica neue', helvetica, sans-serif;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;padding:0;Margin:0\"><div class=\"es-wrapper-color\" style=\"background-color:#F6F6F6\"> <!--[if gte mso 9]><v:background xmlns:v=\"urn:schemas-microsoft-com:vml\" fill=\"t\"> <v:fill type=\"tile\" color=\"#f6f6f6\"></v:fill> </v:background><![endif]--><table class=\"es-wrapper\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;padding:0;Margin:0;width:100%;height:100%;background-repeat:repeat;background-position:center top\"><tr><td valign=\"top\" style=\"padding:0;Margin:0\"><table class=\"es-content\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-content-body\" cellspacing=\"0\" cellpadding=\"0\" bgcolor=\"#ffffff\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:#FFFFFF;width:600px\"><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-bottom:15px\"><h2 style=\"Margin:0;line-height:29px;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;font-size:24px;font-style:normal;font-weight:normal;color:#333333\">Seja bem-vindo a nossa loja</h2>\r\n"
				+ "</td></tr><tr><td style=\"padding:0;Margin:0;font-size:0px\" align=\"center\"><img class=\"adapt-img\" src=\"https://prghxj.stripocdn.email/content/guids/CABINET_3ef18c19ee5479b4c800926df0c08e63/images/35331623515864588.png\" alt style=\"display:block;border:0;outline:none;text-decoration:none;-ms-interpolation-mode:bicubic\" width=\"560\"></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Ola " + entity.getNome() + ", seja bem-vindo!<br></p></td>\r\n"
				+ "</tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Obrigado por escolher nosso servico para realizar suas compras!</p></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:15px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Atualmente, nossa equipe está trabalhando para te atender da melhor forma.</p></td>\r\n"
				+ "</tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Boas compras!</p></td></tr></table></td></tr></table></td></tr></table></td>\r\n"
				+ "</tr></table><table class=\"es-footer\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%;background-color:transparent;background-repeat:repeat;background-position:center top\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-footer-body\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:transparent;width:600px\"><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:20px;Margin:0;font-size:0\" align=\"center\"><table width=\"75%\" height=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:0;Margin:0;border-bottom:1px solid #CCCCCC;background:none;height:1px;width:100%;margin:0px\"></td>";
		emailSender.sendSimpleMessage(entity.getEmail(), "Olá " + entity.getNome() + " seja bem vindo", corpo);
		return mapper.entityToDTO(repository.save(this.hash(entity)));
	}
	
	public ClienteDTO update(ClienteDTO novoCliente) throws ClienteNotFoundException, MessagingException {		
		ClienteEntity cliente = this.findByUserNameOrEmail(novoCliente.getCpf());
		if (novoCliente.getUserName() != null) {
			cliente.setUserName(novoCliente.getUserName());
		}		
		if (novoCliente.getEmail() != null) {
			cliente.setEmail(novoCliente.getEmail());
		}
		if (novoCliente.getNome() != null) {
			cliente.setNome(novoCliente.getNome());
		}
		if (novoCliente.getTelefone() != null) {
			cliente.setTelefone(novoCliente.getTelefone());
		}
		if (novoCliente.getDataNascimento() != null) {
			cliente.setDataNascimento(novoCliente.getDataNascimento());
		}
		emailSender.sendSimpleMessage(cliente.getEmail(), "Olá " + cliente.getNome() + " seja bem vindo", "Seja muito bem vindo ao nosso site!");
		return mapper.entityToDTO(repository.save(this.hash(cliente)));
	}
	
	public String reativaCliente(String cpf) throws ClienteNotFoundException {
		ClienteEntity cliente = this.findByUserNameOrEmail(cpf);
		cliente.setAtivo(true);
		repository.save(cliente);
		return "Cliente reativado!";
	}
	
	public String delete(String userName) throws ClienteNotFoundException {
		ClienteEntity cliente = this.findByUserNameOrEmail(userName);
		cliente.setAtivo(false);
		repository.save(cliente);
		return "Cliente deletado com sucesso!";
	}
	
	public String recuperarSenha(String cpf) throws ClienteNotFoundException, MessagingException {
		Optional<ClienteEntity> cliente = repository.findByAtivoTrueAndUserNameOrEmailOrCpf(cpf, cpf, cpf);
		if (cliente.isPresent() && cliente.get().getCpf().equals(cpf)) {
			var uri = ServletUriComponentsBuilder
					.fromCurrentContextPath()
					.path("cliente/recupera/token/{token}")
					.buildAndExpand(cliente.get().getToken())
					.toUri();
			emailSender.sendSimpleMessage(cliente.get().getEmail(), "Click no link para reativar sua conta", uri.toString());
			return "Verifique seu email";
		}
		throw new ClienteNotFoundException("CPF não encontrado!");
	}
	
	private ClienteEntity hash(ClienteEntity cliente) {
		cliente.setToken(bCrypt.encode(cliente.getEmail() + cliente.getCpf() + cliente.getDataNascimento()));
		return cliente;
	}

	public String updateSenha(String token, ClienteDTONovo dto) throws ClienteNotFoundException {
		ClienteEntity cliente = this.findByUserNameOrEmail(dto.getCpf());
		if (token.equals(cliente.getToken())) {
			cliente.setSenha(dto.getSenha());
			return "Senha atualizada com sucesso!";
		}
		return "Token inválido";
	}
}